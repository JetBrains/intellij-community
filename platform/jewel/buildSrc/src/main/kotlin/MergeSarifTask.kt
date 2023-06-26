import com.contrastsecurity.sarif.Run
import com.contrastsecurity.sarif.SarifSchema210
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import java.net.URI
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction
import com.contrastsecurity.sarif.Result

@CacheableTask
open class MergeSarifTask : SourceTask() {

    @InputFiles
    @SkipWhenEmpty
    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.RELATIVE)
    override fun getSource(): FileTree = super.getSource()

    @get:OutputFile
    val mergedSarifPath: File
        get() = project.rootProject.file("build/reports/static-analysis.sarif")

    @TaskAction
    fun merge() {
        val sarifFiles = source.files

        logger.lifecycle("Merging ${sarifFiles.size} SARIF file(s)...")
        logger.debug(sarifFiles.joinToString("\n") { " *  ${it.path}" })

        val objectMapper = ObjectMapper()
        val merged = SarifSchema210()
            .`with$schema`(URI.create("https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json"))
            .withVersion(SarifSchema210.Version._2_1_0)
            .withRuns(mutableListOf())

        sarifFiles.map { file -> objectMapper.readValue(file.readText(), SarifSchema210::class.java) }
            .flatMap { report -> report.runs }
            .groupBy { run -> run.tool.driver.guid ?: run.tool.driver.name }
            .values
            .filter { it.isNotEmpty() }
            .forEach { runs ->
                val mergedResults = mutableListOf<Result>()
                runs.forEach { mergedResults.addAll(it.results) }
                val mergedRun = runs.first().copy(mergedResults)
                merged.runs.add(mergedRun)
            }

        logger.lifecycle("Merged SARIF file contains ${merged.runs.size} run(s)")
        logger.info("Writing merged SARIF file to $mergedSarifPath...")
        objectMapper.writerWithDefaultPrettyPrinter()
            .writeValue(mergedSarifPath, merged)
    }

    /**
     * Poor man's copying of a Run.
     *
     * Note that this is NOT a deep copy; we're not defensively cloning field values,
     * so mutating the copy will also mutate the original.
     */
    private fun Run.copy(newResults: List<Result> = this.results) = Run()
        .withAddresses(addresses)
        .withArtifacts(artifacts)
        .withAutomationDetails(automationDetails)
        .withBaselineGuid(baselineGuid)
        .withColumnKind(columnKind)
        .withConversion(conversion)
        .withDefaultEncoding(defaultEncoding)
        .withDefaultSourceLanguage(defaultSourceLanguage)
        .withExternalPropertyFileReferences(externalPropertyFileReferences)
        .withGraphs(graphs)
        .withInvocations(invocations)
        .withLanguage(language)
        .withLogicalLocations(logicalLocations)
        .withNewlineSequences(newlineSequences)
        .withOriginalUriBaseIds(originalUriBaseIds)
        .withPolicies(policies)
        .withProperties(properties)
        .withRedactionTokens(redactionTokens)
        .withResults(newResults)
        .withRunAggregates(runAggregates)
        .withSpecialLocations(specialLocations)
        .withTaxonomies(taxonomies)
        .withThreadFlowLocations(threadFlowLocations)
        .withTool(tool)
        .withTranslations(translations)
        .withVersionControlProvenance(versionControlProvenance)
        .withWebRequests(webRequests)
        .withWebResponses(webResponses)
}
