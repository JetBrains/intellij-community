// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.platform.buildScripts.testFramework.distributionContent

import com.intellij.platform.buildScripts.testFramework.createBuildOptionsForTest
import com.intellij.platform.buildScripts.testFramework.doRunTestBuild
import com.intellij.platform.buildScripts.testFramework.spanName
import com.intellij.platform.distributionContent.testFramework.FileEntry
import com.intellij.platform.distributionContent.testFramework.PluginContentReport
import com.intellij.platform.distributionContent.testFramework.deserializeContentData
import com.intellij.platform.distributionContent.testFramework.deserializeModuleList
import com.intellij.platform.distributionContent.testFramework.deserializePluginData
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import com.intellij.util.lang.HashMapZipFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.ProprietaryBuildTools
import org.jetbrains.intellij.build.SoftwareBillOfMaterials
import org.jetbrains.intellij.build.impl.buildDistributions
import org.jetbrains.intellij.build.impl.createBuildContext
import org.jetbrains.intellij.build.impl.createCompilationContext
import org.jetbrains.intellij.build.productLayout.discovery.GenerationResult
import org.jetbrains.intellij.build.productLayout.model.error.FileDiff
import org.jetbrains.intellij.build.productLayout.model.error.XIncludeResolutionError
import org.jetbrains.intellij.build.productLayout.model.error.errorId
import org.jetbrains.intellij.build.productLayout.stats.AnsiStyle
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.util.JpsPathUtil
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestInfo
import org.opentest4j.TestAbortedException
import java.nio.file.Path

private data class PackageResult(
  @JvmField val projectHome: Path,
  @JvmField val jpsProject: JpsProject,
  @JvmField val content: ContentReportList,
)

private data class ContentReportList(
  @JvmField val platform: List<FileEntry>,
  @JvmField val productModules: List<PluginContentReport>,
  @JvmField val bundled: List<PluginContentReport>,
  @JvmField val nonBundled: List<PluginContentReport>,
  @JvmField val moduleSets: Map<String, List<String>>,
)

/**
 * Type alias for a generator validation function.
 * The function takes the project home path and returns validation result
 * containing file diffs and validation errors.
 */
typealias GeneratorValidator = suspend (projectHome: Path, outputProvider: ModuleOutputProvider) -> GenerationResult

@ApiStatus.Internal
fun createContentCheckTests(
  scope: CoroutineScope,
  homePath: Path,
  productProperties: ProductProperties,
  contentYamlPath: String,
  testInfo: TestInfo,
  buildTools: ProprietaryBuildTools = ProprietaryBuildTools.DUMMY,
  checkPlugins: Boolean = true,
  suggestedReviewer: String? = null,
  modelValidator: GeneratorValidator? = null,
): Iterator<DynamicTest> {
  productProperties.buildDocAuthoringAssets = false

  // Setup is async - validation and packaging tasks will await it
  val compilationContextDeferred = scope.async {
    val context = createCompilationContext(
      projectHome = homePath,
      productProperties = productProperties,
      scope = scope,
      options = createBuildOptionsForTest(productProperties = productProperties, homeDir = homePath, testInfo = testInfo).also { it.customizeBuildOptions() },
      setupTracer = false,
    )
    // needed for TC, otherwise modelValidator will fail (as no module compilation outputs)
    context.compileProductionModules()
    context
  }

  // Start both tasks immediately in caller's scope (parallel after context is ready)
  val validationDeferred: Deferred<GenerationResult?> = scope.async {
    val context = compilationContextDeferred.await()
    modelValidator?.invoke(homePath, context.outputProvider)
  }

  val packagingDeferred: Deferred<PackageResult> = scope.async {
    val buildContext = createBuildContext(
      compilationContext = compilationContextDeferred.await(),
      projectHome = homePath,
      productProperties = productProperties,
      proprietaryBuildTools = buildTools,
    )
    computePackageResult(testInfo = testInfo, context = buildContext)
  }

  return sequence {
    // Model Validation trigger - awaits to capture timing
    yield(DynamicTest.dynamicTest("model-generation") {
      runBlocking { validationDeferred.await() }
    })

    @Suppress("RunBlockingInSuspendFunction")
    val validationResult = runBlocking { validationDeferred.await() }
    val validationIssues = validationResult?.allIssues ?: emptyList()

    if (validationIssues.isNotEmpty()) {
      // Check for xi-include errors first - they may cause cascading failures
      val xiIncludeErrors = validationIssues.filterIsInstance<XIncludeResolutionError>()
      for (issue in xiIncludeErrors.ifEmpty { validationIssues }) {
        val testId = if (issue is FileDiff) "file-out-of-sync:${homePath.relativize(issue.path)}" else "model-validation:${issue.errorId()}"
        yield(DynamicTest.dynamicTest(testId) {
          if (issue is FileDiff) {
            val relativePath = homePath.relativize(issue.path).toString()
            val patchText = buildUnifiedDiffText(
              fileName = relativePath,
              originalLines = issue.actualContent.lines(),
              revisedLines = issue.expectedContent.lines(),
            )
            val message = buildString {
              appendLine(issue.context)
              appendLine()
              appendLine("Patch:")
              appendLine(patchText)
            }
            throw FileComparisonFailedError(
              message = message,
              expected = issue.expectedContent,
              actual = issue.actualContent,
              actualFilePath = issue.path.toString(),
            )
          }
          else {
            throw AssertionError("Model validation error:\n${issue.format(AnsiStyle(useAnsi = false))}")
          }
        })
      }
    }
    else {
      producePackagingTests(
        validationResult = validationResult,
        packagingDeferred = packagingDeferred,
        contentYamlPath = contentYamlPath,
        suggestedReviewer = suggestedReviewer,
        checkPlugins = checkPlugins,
      )
    }
  }.iterator()
}

private suspend fun SequenceScope<DynamicTest>.producePackagingTests(
  validationResult: GenerationResult?,
  packagingDeferred: Deferred<PackageResult>,
  contentYamlPath: String,
  suggestedReviewer: String?,
  checkPlugins: Boolean,
) {
  val issues = validationResult?.allIssues ?: emptyList()

  // Packaging - awaits inside test to capture timing
  yield(DynamicTest.dynamicTest("packaging") {
    if (issues.isNotEmpty()) {
      throw TestAbortedException("Skipped: model validation failed")
    }
    runBlocking { packagingDeferred.await() }
  })

  // Content check tests - use packaging result
  if (issues.isNotEmpty()) {
    return
  }

  @Suppress("RunBlockingInSuspendFunction")
  val packageResult = runBlocking { packagingDeferred.await() }

  val projectHome = packageResult.projectHome
  val contentList = packageResult.content

  yield(DynamicTest.dynamicTest("platform") {
    checkThatContentIsNotChanged(
      actualFileEntries = contentList.platform,
      expectedFile = projectHome.resolve(contentYamlPath),
      projectHome = projectHome,
      isBundled = true,
      suggestedReviewer = suggestedReviewer,
    )
  })

  // we do not validate contentList.moduleSets - we have XML generated by DSL, so it is verifiable

  val project = packageResult.jpsProject

  val productModules = toMap(contentList.productModules)
  checkPlugins(
    fileEntries = productModules.values.asSequence(),
    project = project,
    projectHome = projectHome,
    nonBundled = null,
    contentFileName = "module-content.yaml",
  )

  if (checkPlugins) {
    checkPlugins(contentList = contentList, project = project, projectHome = projectHome)
  }
}

private suspend fun SequenceScope<DynamicTest>.checkPlugins(
  contentList: ContentReportList,
  project: JpsProject,
  projectHome: Path,
) {
  // a non-bundled plugin may duplicated bundled one
  // - first check non-bundled: any valid mismatch will lead to test failure
  // - then check bundled: may be a mismatch due to a difference between bundled and non-bundled one

  val bundled = toMap(contentList.bundled)
  val nonBundled = toMap(contentList.nonBundled)

  checkPlugins(
    fileEntries = bundled.values.asSequence(),
    project = project,
    projectHome = projectHome,
    nonBundled = nonBundled,
  )

  checkPlugins(
    fileEntries = nonBundled.values.asSequence().filter { !bundled.containsKey(getPluginContentKey(it)) },
    project = project,
    projectHome = projectHome,
    nonBundled = null,
  )
}

private fun BuildOptions.customizeBuildOptions() {
  // reproducible content report
  randomSeedNumber = 42
  skipCustomResourceGenerators = true
  targetOs = OsFamily.ALL
  targetArch = null
  buildStepsToSkip += listOf(
    BuildOptions.MAVEN_ARTIFACTS_STEP,
    BuildOptions.SEARCHABLE_OPTIONS_INDEX_STEP,
    BuildOptions.BROKEN_PLUGINS_LIST_STEP,
    BuildOptions.FUS_METADATA_BUNDLE_STEP,
    BuildOptions.SCRAMBLING_STEP,
    BuildOptions.PREBUILD_SHARED_INDEXES,
    BuildOptions.SOURCES_ARCHIVE_STEP,
    BuildOptions.VERIFY_CLASS_FILE_VERSIONS,
    BuildOptions.ARCHIVE_PLUGINS,
    BuildOptions.WINDOWS_EXE_INSTALLER_STEP,
    BuildOptions.REPAIR_UTILITY_BUNDLE_STEP,
    SoftwareBillOfMaterials.STEP_ID,
    BuildOptions.LINUX_ARTIFACTS_STEP,
    BuildOptions.THIRD_PARTY_LIBRARIES_LIST_STEP,
    BuildOptions.LOCALIZE_STEP,
    BuildOptions.VALIDATE_PLUGINS_TO_BE_PUBLISHED,
    "JupyterFrontEndResourcesGenerator",
  )
  useReleaseCycleRelatedBundlingRestrictionsForContentReport = false
}

private fun toMap(contentList: List<PluginContentReport>): Map<String, PluginContentReport> {
  val result = contentList.associateByTo(LinkedHashMap(contentList.size)) { getPluginContentKey(it) }
  require(result.size == contentList.size) {
    "Duplicate plugin content entries: ${contentList.groupBy { getPluginContentKey(it) }.filterValues { it.size > 1 }.keys}"
  }
  return result
}

private suspend fun SequenceScope<DynamicTest>.checkPlugins(
  fileEntries: Sequence<PluginContentReport>,
  nonBundled: Map<String, PluginContentReport>?,
  project: JpsProject,
  projectHome: Path?,
  suggestedReviewer: String? = null,
  contentFileName: String = "plugin-content.yaml",
) {
  for (item in fileEntries) {
    val module = project.findModuleByName(item.mainModule) ?: continue
    val contentRoot = Path.of(JpsPathUtil.urlToPath(module.contentRootsList.urls.first()))
    val expectedFile = contentRoot.resolve(contentFileName)

    //if (true) {
    //  java.nio.file.Files.writeString(expectedFile, serializeContentEntries(normalizeContentReport(fileEntries = item.content, short = true)))
    //  continue
    //}

    yield(DynamicTest.dynamicTest(getPluginContentKey(item)) {
      checkThatContentIsNotChanged(
        actualFileEntries = item.content,
        expectedFile = expectedFile,
        projectHome = projectHome!!,
        isBundled = nonBundled != null,
        suggestedReviewer = suggestedReviewer,
      )

      if (nonBundled != null) {
        val nonBundledVersion = nonBundled.get(getPluginContentKey(item)) ?: return@dynamicTest
        val a = normalizeContentReport(fileEntries = item.content, short = true)
        val b = normalizeContentReport(fileEntries = nonBundledVersion.content, short = true)
        if (a != b) {
          throw AssertionError(
            "Bundled plugin content must be equal to non-bundled one." +
            "\nbundled:\n$a" +
            "\nnon-bundled:\n$b"
          )
        }
      }
    })
  }
}

private fun getPluginContentKey(item: PluginContentReport): String {
  return item.mainModule + (if (item.os == null) "" else " (os=${item.os})") + (if (item.arch == null) "" else " (arch=${item.arch})")
}

private suspend fun computePackageResult(testInfo: TestInfo, context: BuildContext): PackageResult {
  return doRunTestBuild(
    context = context,
    writeTelemetry = true,
    checkIntegrityOfEmbeddedFrontend = false,
    checkThatBundledPluginInFrontendArePresent = false,
    traceSpanName = testInfo.spanName,
    build = { context ->
      buildDistributions(context)
      extraValidatePackageResult(context)
    },
  )
}

private fun extraValidatePackageResult(context: BuildContext): PackageResult {
  val file = context.paths.artifactDir.resolve("content-report.zip")

  HashMapZipFile.load(file).use { zip ->
    fun getString(zip: HashMapZipFile, name: String): String {
      return Charsets.UTF_8.decode(requireNotNull(zip.getByteBuffer(name)) { "Cannot find $name in $file" }).toString()
    }

    fun getPlatformData(name: String): List<FileEntry> {
      val data = getString(zip, name)
      try {
        return deserializeContentData(data)
      }
      catch (e: SerializationException) {
        throw RuntimeException("Cannot parse $name in $file\ndata:$data", e)
      }
    }

    fun getData(name: String): List<PluginContentReport> {
      val data = getString(zip, name)
      try {
        return deserializePluginData(data)
      }
      catch (e: SerializationException) {
        throw RuntimeException("Cannot parse $name in $file\ndata:$data", e)
      }
    }

    val moduleSets = zip.entries
      .asSequence()
      .filter { it.name.startsWith("moduleSets/") && it.name.endsWith(".yaml") }
      .associate {
        val moduleSetName = it.name.removePrefix("moduleSets/").removeSuffix(".yaml")
        val yamlData = it.getData(zip).decodeToString()
        moduleSetName to try {
          deserializeModuleList(yamlData)
        }
        catch (e: SerializationException) {
          throw RuntimeException("Cannot parse module set $moduleSetName in $file\ndata:$yamlData", e)
        }
      }

    return PackageResult(
      content = ContentReportList(
        platform = getPlatformData("platform.yaml"),
        productModules = getData("product-modules.yaml"),
        bundled = getData("bundled-plugins.yaml"),
        nonBundled = getData("non-bundled-plugins.yaml"),
        moduleSets = moduleSets,
      ),
      jpsProject = context.project,
      projectHome = context.paths.projectHome,
    )
  }
}
