// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.devDistGenerator

import com.intellij.platform.buildScripts.pluginModelTool.PluginSymbolicPreparationFacts
import com.intellij.platform.buildScripts.pluginModelTool.PluginSymbolicPreparedEffect
import com.intellij.util.io.jarFile
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.dev.DevPluginLayoutAsset
import org.jetbrains.intellij.build.dev.DevPluginLayoutAssetOwner
import org.jetbrains.intellij.build.dev.DevPluginLayoutAssetPreparation
import org.jetbrains.intellij.build.dev.DevPluginLayoutAssetSource
import org.jetbrains.intellij.build.dev.DevPluginLayoutAssetSpec
import org.jetbrains.intellij.build.dev.DevPluginLayoutAssetTransform
import org.jetbrains.intellij.build.dev.DevPluginPreparationOperation
import org.jetbrains.intellij.build.dev.DevPluginReference
import org.jetbrains.intellij.build.devDist.PluginPackingAsset
import org.jetbrains.intellij.build.impl.BazelTargetsInfo
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.java.JpsJavaLibraryType
import org.jetbrains.jps.model.java.JpsJavaModuleType
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.util.JpsPathUtil
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * The binding of the RenderDoc runtime callback: it names one module library container and extracts only the runtime
 * subtree into the plugin root, as the production callback does.
 */
class DevDistRenderDocRuntimeTest {
  @Test
  fun `the runtime asset binds one module library container and a prepared runtime tree`(@TempDir checkout: Path) {
    val libraryName = "jetbrains.rd.client.renderdoc.runtime.linux.x86_64"
    val libraryLabel = "//plugins/renderdoc:test_runtime"
    val bindings = bind(checkout, libraryName = libraryName, libraryLabel = libraryLabel)
    val operationId = "layout-assets:$KEY"
    val output = "$operationId:output"
    val operation = DevPluginPreparationOperation(
      id = operationId,
      kind = "layout-assets",
      inputs = listOf(DevPluginReference(libraryLabel)),
      output = output,
      manifest = "keep",
      layoutAssets = DevPluginLayoutAssetPreparation(
        format = "tree",
        root = "runtime",
        assets = listOf(DevPluginLayoutAsset(
          destination = "",
          sources = listOf(0),
          transform = DevPluginLayoutAssetTransform.archiveTree(stripComponents = 1, includes = listOf("runtime/**")),
        )),
      ),
    )

    assertThat(bindings.catalogueFacts).isEqualTo(
      DevDistPluginCatalogueFacts(additionalLibraries = listOf(DevDistPluginLibraryInput(libraryName = libraryName, moduleName = LIBRARY_MODULE)))
    )
    assertThat(bindings.catalogueFacts.additionalInputs).isEmpty()
    assertThat(bindings.catalogueFacts.fileFacts).isEmpty()
    assertThat(bindings.operations).containsExactly(operation)
    assertThat(bindings.facts).isEqualTo(
      PluginSymbolicPreparationFacts(
        effects = mapOf(
          KEY to PluginSymbolicPreparedEffect(
            operation = operation,
            assets = listOf(PluginPackingAsset(destination = "runtime", inputs = listOf(output), kind = "tree", classPath = false)),
          )
        )
      )
    )
  }

  @Test
  fun `a different platform name keeps the same extraction shape`(@TempDir checkout: Path) {
    val first = bind(checkout, libraryName = "jetbrains.rd.client.renderdoc.runtime.linux.x86_64", libraryLabel = "//plugins/renderdoc:linux")
    val second = bind(checkout, libraryName = "jetbrains.rd.client.renderdoc.runtime.macos.aarch64", libraryLabel = "//plugins/renderdoc:mac")

    assertThat(first.catalogueFacts.additionalLibraries).containsExactly(DevDistPluginLibraryInput("jetbrains.rd.client.renderdoc.runtime.linux.x86_64", LIBRARY_MODULE))
    assertThat(second.catalogueFacts.additionalLibraries).containsExactly(DevDistPluginLibraryInput("jetbrains.rd.client.renderdoc.runtime.macos.aarch64", LIBRARY_MODULE))
    assertThat(second.operations.single().layoutAssets).isEqualTo(first.operations.single().layoutAssets)
  }

  private fun bind(checkout: Path, libraryName: String, libraryLabel: String) = generateDevPluginLayoutAssetBindings(
    key = KEY,
    owner = object : DevPluginLayoutAssetOwner {
      override val devPluginLayoutAssetSpec: DevPluginLayoutAssetSpec = DevPluginLayoutAssetSpec(
        sources = listOf(DevPluginLayoutAssetSource.ModuleLibrary(module = LIBRARY_MODULE, name = libraryName)),
        assets = listOf(DevPluginLayoutAsset(destination = "runtime", sources = listOf(0), transform = DevPluginLayoutAssetTransform.archiveTree(stripComponents = 1, includes = listOf("runtime/**")))),
      )
    },
    requestedFormat = "tree",
    index = renderDocIndex(checkout, libraryName = libraryName, libraryLabel = libraryLabel),
    binder = CommunityDevDistHalf.assetBinder,
    outputProvider = renderDocOutputProvider(checkout, libraryName = libraryName),
  )

  private fun renderDocIndex(checkout: Path, libraryName: String, libraryLabel: String): DevDistBazelIndex {
    return DevDistBazelIndex(
      targets = BazelTargetsInfo.TargetsFile(
        modules = mapOf(
          LIBRARY_MODULE to BazelTargetsInfo.TargetsFileModuleDescription(
            productionTargets = listOf("//plugins/renderdoc:owner"),
            productionJars = emptyList(),
            testTargets = emptyList(),
            testJars = emptyList(),
            exports = emptyList(),
            moduleLibraries = mapOf(
              libraryName to BazelTargetsInfo.LibraryDescription(
                target = libraryLabel,
                jars = listOf("renderdoc-runtime.zip"),
                jarTargets = listOf("//plugins/renderdoc:runtime_zip"),
                sourceJars = emptyList(),
              )
            ),
          )
        ),
        projectLibraries = emptyMap(),
        pluginDistributionTargets = emptyMap(),
      ),
      projectRoot = checkout,
    )
  }

  private fun renderDocOutputProvider(checkout: Path, libraryName: String): SourceRootModuleOutputProvider {
    val project = JpsElementFactory.getInstance().createModel().project
    val module = project.addModule(LIBRARY_MODULE, JpsJavaModuleType.INSTANCE)
    val baseLibrary = module.addModuleLibrary("jetbrains.rd.client.renderdoc", JpsJavaLibraryType.INSTANCE)
    val library = module.addModuleLibrary(libraryName, JpsJavaLibraryType.INSTANCE)
    val baseArchive = checkout.resolve("renderdoc-base.jar")
    val archive = checkout.resolve("renderdoc-runtime.zip")
    jarFile {
      file("renderdoc.jar", "base")
    }.generate(baseArchive)
    jarFile {
      dir("runtime") {
        file("renderdoc.dll", "runtime")
      }
      dir("docs") {
        file("readme.txt", "ignored")
      }
    }.generate(archive)
    baseLibrary.addRoot(JpsPathUtil.pathToUrl(baseArchive.toString()), JpsOrderRootType.COMPILED)
    library.addRoot(JpsPathUtil.pathToUrl(archive.toString()), JpsOrderRootType.COMPILED)
    return SourceRootModuleOutputProvider(project)
  }
}

private const val KEY = "platform-custom-asset:0"
private const val LIBRARY_MODULE = "intellij.libraries.rd.client.renderdoc"