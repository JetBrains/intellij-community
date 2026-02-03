import java.io.File
import java.util.regex.PatternSyntaxException
import org.gradle.api.GradleException
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class ValidatePublicApiTask : SourceTask() {
    @get:Input abstract var excludedClassRegexes: Set<String>

    init {
        group = "verification"

        // The output is never really used, it is here for cacheability reasons only
        outputs.file(project.layout.buildDirectory.file("apiValidationRun"))
    }

    @TaskAction
    fun validatePublicApi() {
        val violations = mutableMapOf<File, Set<String>>()
        val excludedRegexes =
            excludedClassRegexes
                .map {
                    try {
                        it.toRegex()
                    } catch (_: PatternSyntaxException) {
                        throw GradleException("Invalid data exclusion regex: '$it'")
                    }
                }
                .toSet()

        source.forEach { apiDumpFile ->
            logger.lifecycle("Validating public API from file ${apiDumpFile.path}")

            apiDumpFile.useLines { lines ->
                val actualDataClasses = findDataClasses(lines).filterExclusions(excludedRegexes)

                if (actualDataClasses.isNotEmpty()) {
                    violations[apiDumpFile] = actualDataClasses
                }
            }
        }

        if (violations.isNotEmpty()) {
            val message = buildString {
                appendLine("Data classes are not allowed in public API. Found violations:")
                appendLine()

                for ((file, dataClasses) in violations.entries) {
                    appendLine("In file ${file.path}:")
                    for (dataClass in dataClasses) {
                        appendLine(" * ${dataClass.replace("/", ".")}")
                    }
                    appendLine()
                }

                appendLine(
                    "Avoid using data classes in public API: https://jakewharton.com/public-api-challenges-in-kotlin/."
                )
                appendLine(
                    "Turn them into regular classes, implement equals()/hashCode()/toString(), " +
                        "and add the @GenerateDataFunctions annotation."
                )
                appendLine(
                    "For specific cases, you can exclude a data class from the validation. " +
                        "For this, just add it to the 'apiValidation.excludedClassRegexes' block in " +
                        "the module's build.gradle.kts."
                )
            }

            throw GradleException(message)
        } else {
            logger.lifecycle("No public API violations found.")
        }
    }

    private fun findDataClasses(lines: Sequence<String>): Set<String> {
        var currentClassFqn: String? = null
        var isCurrentClassCandidate = false
        val dataClasses = mutableMapOf<String, DataClassInfo>()

        for (line in lines) {
            if (line.isBlank()) continue

            // If the line starts with -, it's a member of a class.
            if (line.startsWith("-")) {
                // If we haven't found a class context yet or the current member isn't from
                // a data class candidate, just ignore this member.
                if (currentClassFqn == null || !isCurrentClassCandidate) continue

                // We are inside a class, so check for data class methods.
                val info = dataClasses.getOrPut(currentClassFqn) { DataClassInfo() }

                // Detect copy() method and the $default version.
                if ("copy(" in line || "copy\$default(" in line) {
                    info.hasCopyMethod = true
                }

                // Detect componentN() methods
                if (line.contains(Regex("""component\d+\("""))) {
                    info.hasComponentMethod = true
                }
            } else {
                if (line.startsWith("f:")) {
                    currentClassFqn = line.substringAfter("f:")
                    isCurrentClassCandidate = true
                } else {
                    isCurrentClassCandidate = false
                }
            }
        }

        return dataClasses.filterValues { it.isDataClass() }.keys
    }

    private fun Set<String>.filterExclusions(excludedRegexes: Set<Regex>): Set<String> {
        if (excludedRegexes.isEmpty()) return this

        return filterNot { dataClassFqn ->
                val isExcluded = excludedRegexes.any { it.matchEntire(dataClassFqn) != null }

                if (isExcluded) {
                    logger.lifecycle("  Ignoring excluded data class $dataClassFqn")
                }
                isExcluded
            }
            .toSet()
    }
}

@Suppress("DataClassShouldBeImmutable") // Only used in a loop, saves memory and is faster
private data class DataClassInfo(var hasCopyMethod: Boolean = false, var hasComponentMethod: Boolean = false) {
    fun isDataClass(): Boolean = hasCopyMethod && hasComponentMethod
}
