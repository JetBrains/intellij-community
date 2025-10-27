// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")
package com.intellij.platform.buildScripts.testFramework.distributionContent

import com.intellij.platform.buildScripts.testFramework.runTestBuild
import com.intellij.platform.buildScripts.testFramework.spanName
import com.intellij.platform.distributionContent.testFramework.FileEntry
import com.intellij.platform.distributionContent.testFramework.PluginContentReport
import com.intellij.platform.distributionContent.testFramework.deserializeContentData
import com.intellij.platform.distributionContent.testFramework.deserializeModuleList
import com.intellij.platform.distributionContent.testFramework.deserializePluginData
import com.intellij.util.lang.HashMapZipFile
import kotlinx.serialization.SerializationException
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.ProprietaryBuildTools
import org.jetbrains.intellij.build.SoftwareBillOfMaterials
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.util.JpsPathUtil
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.function.Executable
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

@ApiStatus.Internal
fun createContentCheckTests(
  projectHomePath: Path,
  productProperties: ProductProperties,
  contentYamlPath: String,
  testInfo: TestInfo,
  buildTools: ProprietaryBuildTools = ProprietaryBuildTools.DUMMY,
  checkPlugins: Boolean = true,
  suggestedReviewer: String? = null,
): Iterator<DynamicTest> {
  val packageResult by lazy {
    lateinit var result: PackageResult
    computePackageResult(
      productProperties = productProperties,
      testInfo = testInfo,
      contentConsumer = {
        result = it
      },
      homePath = projectHomePath,
      buildTools = buildTools,
    )
    result
  }

  return sequence {
    val projectHome = packageResult.projectHome
    val contentList = packageResult.content

    yield(DynamicTest.dynamicTest("${testInfo.spanName}(platform)") {
      checkThatContentIsNotChanged(
        actualFileEntries = contentList.platform,
        expectedFile = projectHome.resolve(contentYamlPath),
        projectHome = projectHome,
        isBundled = true,
        suggestedReviewer = suggestedReviewer,
      )
    })

    for ((moduleSetName, moduleSetData) in contentList.moduleSets) {
      yield(DynamicTest.dynamicTest("${testInfo.spanName}(moduleSet:$moduleSetName)") {
        checkThatModuleListIsNotChanged(
          actual = moduleSetData,
          expectedFile = projectHome.resolve("build/expected/moduleSets/$moduleSetName.yaml"),
          projectHome = projectHome,
          suggestedReviewer = suggestedReviewer,
        )
      })
    }

    val project = packageResult.jpsProject

    val productModules = toMap(contentList.productModules)
    checkPlugins(
      fileEntries = productModules.values.asSequence(),
      project = project,
      projectHome = projectHome,
      nonBundled = null,
      testInfo = testInfo,
      contentFileName = "module-content.yaml",
    )

    if (checkPlugins) {
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
        testInfo = testInfo,
      )

      checkPlugins(
        fileEntries = nonBundled.values.asSequence().filter { !bundled.containsKey(getPluginContentKey(it)) },
        project = project,
        projectHome = projectHome,
        nonBundled = null,
        testInfo = testInfo,
      )
    }
  }.iterator()
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
  testInfo: TestInfo,
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

    yield(DynamicTest.dynamicTest("${testInfo.spanName}(${getPluginContentKey(item)})", Executable {
      checkThatContentIsNotChanged(
        actualFileEntries = item.content,
        expectedFile = expectedFile,
        projectHome = projectHome!!,
        isBundled = nonBundled != null,
        suggestedReviewer = suggestedReviewer,
      )

      if (nonBundled != null) {
        val nonBundledVersion = nonBundled.get(getPluginContentKey(item)) ?: return@Executable
        val a = normalizeContentReport(fileEntries = item.content, short = true)
        val b = normalizeContentReport(fileEntries = nonBundledVersion.content, short = true)
        if (a != b) {
          "Bundled plugin content must be equal to non-bundled one." +
          "\nbundled:\n$a" +
          "\nnon-bundled:\n$b"
        }
      }
    }))
  }
}

private fun getPluginContentKey(item: PluginContentReport): String = item.mainModule + (if (item.os == null) "" else " (os=${item.os})")

private fun computePackageResult(
  productProperties: ProductProperties,
  testInfo: TestInfo,
  contentConsumer: (PackageResult) -> Unit,
  homePath: Path,
  buildTools: ProprietaryBuildTools = ProprietaryBuildTools.DUMMY,
) {
  productProperties.buildDocAuthoringAssets = false
  runTestBuild(
    homeDir = homePath,
    productProperties = productProperties,
    buildTools = buildTools,
    testInfo = testInfo,
    isReproducibilityTestAllowed = false,
    checkIntegrityOfEmbeddedFrontend = false,
    onSuccess = { context ->
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

        contentConsumer(
          PackageResult(
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
        )
      }
    },
    buildOptionsCustomizer = {
      // reproducible content report
      it.randomSeedNumber = 42
      it.skipCustomResourceGenerators = true
      it.targetOs = OsFamily.ALL
      it.targetArch = null
      it.buildStepsToSkip += listOf(
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
      it.useReleaseCycleRelatedBundlingRestrictionsForContentReport = false
    }
  )
}