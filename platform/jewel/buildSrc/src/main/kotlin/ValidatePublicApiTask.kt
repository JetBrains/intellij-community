import java.io.File
import java.util.Stack
import java.util.regex.PatternSyntaxException
import org.gradle.api.GradleException
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction

@CacheableTask
open class ValidatePublicApiTask : SourceTask() {

    @Input var excludedClassRegexes: Set<String> = emptySet()

    init {
        group = "verification"

        // The output is never really used, it is here for cacheability reasons only
        outputs.file(project.layout.buildDirectory.file("apiValidationRun"))
    }

    private val classFqnRegex = "public (?:\\w+ )*class (\\S+)\\b".toRegex()

    @Suppress("ConvertToStringTemplate") // The odd concatenation is needed because of $; escapes get confused
    private val copyMethodRegex = ("public static synthetic fun copy(-\\w+)?" + "\\$" + "default\\b").toRegex()

    @TaskAction
    fun validatePublicApi() {
        logger.info("Validating ${source.files.size} API file(s)...")

        val violations = mutableMapOf<File, Set<String>>()
        val excludedRegexes =
            excludedClassRegexes
                .map {
                    try {
                        it.toRegex()
                    } catch (ignored: PatternSyntaxException) {
                        throw GradleException("Invalid data exclusion regex: '$it'")
                    }
                }
                .toSet()

        inputs.files.forEach { apiFile ->
            logger.lifecycle("Validating public API from file ${apiFile.path}")

            apiFile.useLines { lines ->
                val actualDataClasses = findDataClasses(lines).filterExclusions(excludedRegexes)

                if (actualDataClasses.isNotEmpty()) {
                    violations[apiFile] = actualDataClasses
                }
            }
        }

        if (violations.isNotEmpty()) {
            val message = buildString {
                appendLine("Data classes found in public API.")
                appendLine()

                for ((file, dataClasses) in violations.entries) {
                    appendLine("In file ${file.path}:")
                    for (dataClass in dataClasses) {
                        appendLine(" * ${dataClass.replace("/", ".")}")
                    }
                    appendLine()
                }
            }

            throw GradleException(message)
        } else {
            logger.lifecycle("No public API violations found.")
        }
    }

    private fun findDataClasses(lines: Sequence<String>): Set<String> {
        val currentClassStack = Stack<String>()
        val dataClasses = mutableMapOf<String, DataClassInfo>()

        for (line in lines) {
            if (line.isBlank()) continue

            val matchResult = classFqnRegex.find(line)
            if (matchResult != null) {
                val classFqn = matchResult.groupValues[1]
                currentClassStack.push(classFqn)
                continue
            }

            if (line.contains("}")) {
                currentClassStack.pop()
                continue
            }

            val fqn = currentClassStack.peek()
            if (copyMethodRegex.find(line) != null) {
                val info = dataClasses.getOrPut(fqn) { DataClassInfo(fqn) }
                info.hasCopyMethod = true
            } else if (line.contains("public static final synthetic fun box-impl")) {
                val info = dataClasses.getOrPut(fqn) { DataClassInfo(fqn) }
                info.isLikelyValueClass = true
            }
        }

        val actualDataClasses = dataClasses.filterValues { it.hasCopyMethod && !it.isLikelyValueClass }.keys
        return actualDataClasses
    }

    private fun Set<String>.filterExclusions(excludedRegexes: Set<Regex>): Set<String> {
        if (excludedRegexes.isEmpty()) return this

        return filterNot { dataClassFqn ->
                val isExcluded = excludedRegexes.any { it.matchEntire(dataClassFqn) != null }

                if (isExcluded) {
                    logger.info("  Ignoring excluded data class $dataClassFqn")
                }
                isExcluded
            }
            .toSet()
    }
}

@Suppress("DataClassShouldBeImmutable") // Only used in a loop, saves memory and is faster
private data class DataClassInfo(
    val fqn: String,
    var hasCopyMethod: Boolean = false,
    var isLikelyValueClass: Boolean = false,
)
