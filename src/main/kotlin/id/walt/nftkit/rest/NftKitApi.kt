package id.walt.nftkit.rest


import cc.vileda.openapi.dsl.components
import cc.vileda.openapi.dsl.externalDocs
import cc.vileda.openapi.dsl.info
import cc.vileda.openapi.dsl.securityScheme
import com.beust.klaxon.Klaxon
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.exc.InvalidFormatException
import id.walt.nftkit.Values
import id.walt.nftkit.rest.RootController.healthDocs
import id.walt.rest.ErrorResponse
import id.walt.rest.OpenAPIUtils.documentedIgnored
import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.*
import io.javalin.core.util.RouteOverviewPlugin
import io.javalin.plugin.json.JavalinJackson
import io.javalin.plugin.json.JsonMapper
import io.javalin.plugin.openapi.InitialConfigurationCreator
import io.javalin.plugin.openapi.OpenApiOptions
import io.javalin.plugin.openapi.OpenApiPlugin
import io.javalin.plugin.openapi.dsl.documented
import io.javalin.plugin.openapi.ui.ReDocOptions
import io.javalin.plugin.openapi.ui.SwaggerOptions
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server

object NftKitApi {

//    private val log = KotlinLogging.logger {}

    internal const val DEFAULT_CORE_API_PORT = 7000

    internal const val DEFAULT_BIND_ADDRESS = "127.0.0.1"

    /**
     * Currently used instance of the Nft Kit API server
     */
    var api: Javalin? = null

    /**
     * Start NFT KIT REST API
     * @param apiTargetUrls (optional): add URLs to Swagger documentation for easy testing
     * @param bindAddress (default: 127.0.0.1): select address to bind on to, e.g. 0.0.0.0 for all interfaces
     * @param port (default: 7000): select port to listen on
     */
    fun start(
        port: Int = DEFAULT_CORE_API_PORT,
        bindAddress: String = DEFAULT_BIND_ADDRESS,
        apiTargetUrls: List<String> = listOf()
    ) {
//        log.info { "Starting NFT KIT API ...\n" }

       api = Javalin.create {
            it.apply {
                registerPlugin(RouteOverviewPlugin("/api-routes"))

                registerPlugin(OpenApiPlugin(OpenApiOptions(InitialConfigurationCreator {
                    OpenAPI().apply {
                        info {
                            title = "walt.id Nft Kit API"
                            description = "The walt.id public API documentation"
                            contact = Contact().apply {
                                name = "walt.id"
                                url = "https://walt.id"
                                email = "office@walt.id"
                            }
                            version = Values.version
                        }
                        servers = listOf(
                            Server().url("/"),
                            *apiTargetUrls.map { Server().url(it) }.toTypedArray()
                        )
                        externalDocs {
                            description = "walt.id docs"
                            url = "https://docs.walt.id"
                        }

                        components {
                            securityScheme {
                                name = "bearerAuth"
                                type = SecurityScheme.Type.HTTP
                                scheme = "bearer"
                                `in` = SecurityScheme.In.HEADER
                                description = "HTTP Bearer Token authentication"
                                bearerFormat = "JWT"
                            }
                        }
                    }
                }).apply {
                    path("/v1/api-documentation")
                    swagger(SwaggerOptions("/v1/swagger").title("walt.id Nft Kit API"))
                    reDoc(ReDocOptions("/v1/redoc").title("walt.id Nft Kit API"))
                }))

                val mapper: ObjectMapper = com.fasterxml.jackson.databind.json.JsonMapper.builder()
                    .findAndAddModules()
                    .build()

                this.jsonMapper(object : JsonMapper {
                    override fun toJsonString(obj: Any): String {
                        return Klaxon().toJsonString(obj)
                    }

                    override fun <T : Any?> fromJsonString(json: String, targetClass: Class<T>): T {
                        return JavalinJackson(mapper).fromJsonString(json, targetClass)
                    }
                })

                //addStaticFiles("/static")
            }

            it.enableCorsForAllOrigins()

            it.enableDevLogging()
        }.routes {
            get("", documented(documentedIgnored(), RootController::root))
            get("health", documented(healthDocs(), RootController::health))
            path("v1") {
                path("key") {
                    get("", documented(KeyController.listDocs(), KeyController::list))
                    get("{id}", documented(KeyController.loadDocs(), KeyController::load))
                    delete("{id}", documented(KeyController.deleteDocs(), KeyController::delete))
                    post("gen", documented(KeyController.genDocs(), KeyController::gen))
                    post("import", documented(KeyController.importDocs(), KeyController::import))
                    post("export", documented(KeyController.exportDocs(), KeyController::export))
                }
            }
        }.exception(InvalidFormatException::class.java) { e, ctx ->
//            log.error(e.stackTraceToString())
            ctx.json(ErrorResponse(e.message ?: " Unknown application error", 400))
            ctx.status(400)
        }.exception(IllegalArgumentException::class.java) { e, ctx ->
//            log.error(e.stackTraceToString())
            ctx.json(ErrorResponse(e.message ?: " Unknown application error", 400))
            ctx.status(400)
        }.exception(Exception::class.java) { e, ctx ->
//            log.error(e.stackTraceToString())
            ctx.json(ErrorResponse(e.message ?: " Unknown server error", 500))
            ctx.status(500)
        }.start(bindAddress, port)
    }

    /**
     * Stop Nft Kit API if it's currently running
     */
    fun stop() = api?.stop()
}

fun main() {
    NftKitApi.start()
}