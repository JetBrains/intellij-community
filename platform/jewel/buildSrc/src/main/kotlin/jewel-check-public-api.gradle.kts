import org.jetbrains.jewel.buildlogic.apivalidation.ApiValidationExtension
import org.jetbrains.jewel.buildlogic.metalava.MetalavaConfigurer

plugins { kotlin("jvm") }

val extension = project.extensions.create("publicApiValidation", ApiValidationExtension::class.java)

with(extension) { excludedClassRegexes.convention(emptySet()) }

private val versionCatalog = project.extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

MetalavaConfigurer(project, versionCatalog).configure()

tasks {
    val validatePublicApi =
        register<ValidatePublicApiTask>("validatePublicApi") {
            source(project.fileTree(".") { include("api-dump*.txt") })

            excludedClassRegexes = project.the<ApiValidationExtension>().excludedClassRegexes.get()
        }

    named("check") { dependsOn(validatePublicApi) }
}
