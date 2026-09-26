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
    
    register("cleanBaselinesApi") {
        group = "metalava"
        description = "Resets metalava/<module>-baseline[-stable]-current.txt to only the baseline header; warns with previous content if it differed."

        val expected = "// Baseline format: 1.0\n"

        // This is the *current module* directory (where this build.gradle.kts lives)
        val moduleDir = layout.projectDirectory.asFile

        val baselineFiles = listOf(
            moduleDir.resolve("metalava/${project.name}-baseline-current.txt"),
            moduleDir.resolve("metalava/${project.name}-baseline-stable-current.txt"),
        )

        inputs.property("expectedBaselineHeader", expected)
        outputs.files(baselineFiles)

        doLast {
            baselineFiles.forEach { file ->
                val old = if (file.isFile) file.readText() else null

                file.parentFile.mkdirs()
                file.writeText(expected)

                if (old != null && old != expected) {
                    logger.warn(
                        buildString {
                            appendLine("Replaced content of ${file.relativeTo(moduleDir)} with baseline header only.")
                            appendLine("Previous content was:")
                            appendLine("-----8<-----")
                            append(old.trimEnd())
                            appendLine()
                            appendLine("----->8-----")
                        }
                    )
                }
            }
        }
    }
}
