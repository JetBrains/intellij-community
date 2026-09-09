// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework

import com.intellij.platform.buildData.productInfo.ProductInfoLayoutItemKind
import com.intellij.platform.productMode.ProductMode
import com.intellij.util.xml.dom.readXmlAsModel
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.ProprietaryBuildTools
import org.jetbrains.intellij.build.impl.readBuiltinModulesFile
import org.junit.jupiter.api.TestInfo
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream
import kotlin.io.path.name

/**
 * Checks that frontend distribution (ex JetBrains Client) described by [frontendProperties] can be built successfully.
 */
fun runTestBuildForFrontend(
  homePath: Path,
  frontendProperties: ProductProperties,
  buildTools: ProprietaryBuildTools,
  testInfo: TestInfo,
  softly: SoftAssertions,
) {
  runTestBuild(
    homeDir = homePath,
    productProperties = frontendProperties,
    buildTools = buildTools,
    testInfo = testInfo,
    onSuccess = { context ->
      verifyBuiltInModules(context.paths.artifactDir.resolve("${context.applicationInfo.productCode}-builtinModules.json"))
      assertThat(context.useModularLoader)
        .withFailMessage { "Frontend distribution must use the modular loader, but $frontendProperties doesn't use it" }
        .isTrue()
      val rootModule = frontendProperties.rootModuleForModularLoader
      assertThat(rootModule)
        .withFailMessage { "Root module for the modular loader is not specified in $frontendProperties" }
        .isNotNull()
      RuntimeModuleRepositoryChecker.checkProductModules(rootModule!!, context, softly)
      RuntimeModuleRepositoryChecker.checkBundledPluginsArePresent(rootModule, context, isEmbeddedVariant = false, softly)
      checkRefusedModulesStayInDescriptorWithoutJar(context, softly)
    }
  )
}

/**
 * A content module the product mode refuses stays in the built `plugin.xml` with its body, and the plugin holds no jar of it.
 * The run time excludes the module from its descriptor, so the distribution needs the descriptor and not the jar.
 */
private fun checkRefusedModulesStayInDescriptorWithoutJar(context: BuildContext, softly: SoftAssertions) {
  if (context.productProperties.productMode == ProductMode.MONOLITH) {
    return
  }
  val filter = context.getContentModuleFilter()
  val bundled = context.getBundledPluginModules().toHashSet()
  var checked = 0
  for (layout in context.productProperties.productLayout.pluginLayouts.value) {
    if (layout.mainModule !in bundled) continue
    val pluginDir = context.paths.distAllDir.resolve("plugins").resolve(layout.directoryName)
    val mainJar = pluginDir.resolve("lib").resolve(layout.getMainJarName())
    // a plugin of one operating system is not under dist.all
    if (!Files.exists(mainJar)) continue
    val pluginXml = readZipEntry(mainJar, "META-INF/plugin.xml") ?: continue
    val jars = Files.walk(pluginDir).use { stream -> stream.map { it.name }.filter { it.endsWith(".jar") }.toList() }.toHashSet()
    for (content in readXmlAsModel(pluginXml).children("content")) {
      for (module in content.children("module")) {
        val name = module.getAttributeValue("name") ?: continue
        val loading = module.getAttributeValue("loading")
        if (loading == "required" || loading == "embedded") continue
        if (filter.isOptionalModuleIncluded(moduleName = name.substringBeforeLast('/'), pluginMainModuleName = layout.mainModule)) continue
        checked++
        if (module.content.isNullOrBlank()) {
          softly.fail<Unit>("'$name' of '${layout.mainModule}' is refused by $filter, and its <module/> in the built plugin.xml has no embedded body. The run time needs the body to exclude the module.")
        }
        if ("$name.jar" in jars) {
          softly.fail<Unit>("'$name' of '${layout.mainModule}' is refused by $filter, and the plugin directory still holds $name.jar.")
        }
      }
    }
  }
  softly.assertThat(checked).withFailMessage { "the frontend build refused no plugin module, so the check ran over nothing" }.isGreaterThan(0)
}

private fun readZipEntry(jar: Path, entryName: String): ByteArray? {
  return ZipInputStream(Files.newInputStream(jar)).use { zip ->
    generateSequence { zip.nextEntry }.firstOrNull { it.name == entryName }?.let { zip.readAllBytes() }
  }
}

private fun verifyBuiltInModules(file: Path) {
  val data = readBuiltinModulesFile(file)
  assertThat(data.fileExtensions).isEmpty()
  val modules = data.layout.asSequence().filter { it.kind == ProductInfoLayoutItemKind.pluginAlias }.map { it.name }.toHashSet()
  assertThat(modules)
    .contains("com.intellij.jetbrains.client", "com.intellij.modules.platform")
    .doesNotContain("com.intellij.modules.remoteServers")
}
