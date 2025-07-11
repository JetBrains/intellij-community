import org.jetbrains.jewel.buildlogic.apivalidation.ApiValidationExtension

plugins {
    kotlin("jvm")
}

val extension = project.extensions.create("publicApiValidation", ApiValidationExtension::class.java)

with(extension) { excludedClassRegexes.convention(emptySet()) }

tasks {
    val validatePublicApi =
        register<ValidatePublicApiTask>("validatePublicApi") {
            source(
                project.fileTree(".") {
                    include("api-dump*.txt")
                }
            )

            excludedClassRegexes = project.the<ApiValidationExtension>().excludedClassRegexes.get()
        }

    named("check") { dependsOn(validatePublicApi) }
}
