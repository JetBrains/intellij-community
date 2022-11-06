// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.intellij.build.impl

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.ProductModulesLayout
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.w3c.dom.Element
import java.nio.file.Files
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory

private const val UTIL_JAR = "util.jar"
private const val UTIL_RT_JAR = "util_rt.jar"

private val PLATFORM_API_MODULES = persistentListOf(
  "intellij.platform.analysis",
  "intellij.platform.builtInServer",
  "intellij.platform.core",
  "intellij.platform.diff",
  "intellij.platform.vcs.dvcs",
  "intellij.platform.editor",
  "intellij.platform.externalSystem",
  "intellij.platform.externalSystem.dependencyUpdater",
  "intellij.platform.codeStyle",
  "intellij.platform.indexing",
  "intellij.platform.jps.model",
  "intellij.platform.lang.core",
  "intellij.platform.lang",
  "intellij.platform.lvcs",
  "intellij.platform.ml",
  "intellij.platform.ide",
  "intellij.platform.ide.core",
  "intellij.platform.projectModel",
  "intellij.platform.remote.core",
  "intellij.platform.remoteServers.agent.rt",
  "intellij.platform.remoteServers",
  "intellij.platform.tasks",
  "intellij.platform.usageView",
  "intellij.platform.vcs.core",
  "intellij.platform.vcs",
  "intellij.platform.vcs.log",
  "intellij.platform.vcs.log.graph",
  "intellij.platform.execution",
  "intellij.platform.debugger",
  "intellij.platform.webSymbols",
  "intellij.xml.analysis",
  "intellij.xml",
  "intellij.xml.psi",
  "intellij.xml.structureView",
  "intellij.platform.concurrency",
)

/**
 * List of modules which are included into lib/app.jar in all IntelliJ based IDEs.
 */
private val PLATFORM_IMPLEMENTATION_MODULES = persistentListOf(
  "intellij.platform.analysis.impl",
  "intellij.platform.builtInServer.impl",
  "intellij.platform.core.impl",
  "intellij.platform.ide.core.impl",
  "intellij.platform.diff.impl",
  "intellij.platform.editor.ex",
  "intellij.platform.codeStyle.impl",
  "intellij.platform.indexing.impl",
  "intellij.platform.elevation",
  "intellij.platform.elevation.client",
  "intellij.platform.elevation.common",
  "intellij.platform.elevation.daemon",
  "intellij.platform.externalProcessAuthHelper",
  "intellij.platform.refactoring",
  "intellij.platform.inspect",
  "intellij.platform.lang.impl",
  "intellij.platform.workspaceModel.storage",
  "intellij.platform.workspaceModel.jps",
  "intellij.platform.lvcs.impl",
  "intellij.platform.ide.impl",
  "intellij.platform.projectModel.impl",
  "intellij.platform.macro",
  "intellij.platform.execution.impl",
  "intellij.platform.wsl.impl",
  "intellij.platform.externalSystem.impl",
  "intellij.platform.scriptDebugger.protocolReaderRuntime",
  "intellij.regexp",
  "intellij.platform.remoteServers.impl",
  "intellij.platform.scriptDebugger.backend",
  "intellij.platform.scriptDebugger.ui",
  "intellij.platform.smRunner",
  "intellij.platform.smRunner.vcs",
  "intellij.platform.structureView.impl",
  "intellij.platform.tasks.impl",
  "intellij.platform.testRunner",
  "intellij.platform.debugger.impl",
  "intellij.platform.configurationStore.impl",
  "intellij.platform.serviceContainer",
  "intellij.platform.objectSerializer",
  "intellij.platform.diagnostic",
  "intellij.platform.diagnostic.telemetry",
  "intellij.platform.core.ui",
  "intellij.platform.credentialStore",
  "intellij.platform.credentialStore.ui",
  "intellij.platform.dependenciesToolwindow",
  "intellij.platform.rd.community",
  "intellij.platform.ml.impl",
  "intellij.remoteDev.util",
  "intellij.platform.feedback",
  "intellij.platform.warmup",
  "intellij.platform.buildScripts.downloader",
  "intellij.idea.community.build.dependencies",
  "intellij.platform.usageView.impl",
  "intellij.platform.ml.impl",
  "intellij.platform.tips",
)

object PlatformModules {
  const val PRODUCT_JAR = "product.jar"

  internal val CUSTOM_PACK_MODE: Map<String, LibraryPackMode> = persistentMapOf(
    "jetbrains-annotations-java5" to LibraryPackMode.STANDALONE_SEPARATE_WITHOUT_VERSION_NAME,
    "intellij-coverage" to LibraryPackMode.STANDALONE_SEPARATE,
  )

  internal fun collectPlatformModules(to: MutableCollection<String>) {
    to.addAll(PLATFORM_API_MODULES)
    to.addAll(PLATFORM_IMPLEMENTATION_MODULES)
  }

  internal fun hasPlatformCoverage(productLayout: ProductModulesLayout, enabledPluginModules: Set<String>, context: BuildContext): Boolean {
    val modules = LinkedHashSet<String>()
    modules.addAll(productLayout.getIncludedPluginModules(enabledPluginModules))
    modules.addAll(PLATFORM_API_MODULES)
    modules.addAll(PLATFORM_IMPLEMENTATION_MODULES)
    modules.addAll(productLayout.productApiModules)
    modules.addAll(productLayout.productImplementationModules)
    modules.addAll(productLayout.additionalPlatformJars.values())

    val coverageModuleName = "intellij.platform.coverage"
    if (modules.contains(coverageModuleName)) {
      return true
    }

    for (moduleName in modules) {
      var contains = false
      JpsJavaExtensionService.dependencies(context.findRequiredModule(moduleName))
        .productionOnly()
        .processModules { module ->
          if (!contains && module.name == coverageModuleName) {
            contains = true
          }
        }

      if (contains) {
        return true
      }
    }

    return false
  }

  private fun jar(relativeJarPath: String, moduleNames: Collection<String>, productLayout: ProductModulesLayout, layout: PlatformLayout) {
    for (moduleName in moduleNames) {
      if (!productLayout.excludedModuleNames.contains(moduleName)) {
        layout.withModule(moduleName, relativeJarPath)
      }
    }
  }

  private fun addModule(moduleName: String, productLayout: ProductModulesLayout, layout: PlatformLayout) {
    if (!productLayout.excludedModuleNames.contains(moduleName)) {
      layout.withModule(moduleName)
    }
  }

  private fun addModule(moduleName: String, relativeJarPath: String, productLayout: ProductModulesLayout, layout: PlatformLayout) {
    if (!productLayout.excludedModuleNames.contains(moduleName)) {
      layout.withModule(moduleName, relativeJarPath)
    }
  }

  internal fun createPlatformLayout(productLayout: ProductModulesLayout,
                                    hasPlatformCoverage: Boolean,
                                    additionalProjectLevelLibraries: SortedSet<ProjectLibraryData>,
                                    context: BuildContext): PlatformLayout {
    val layout = PlatformLayout()
    // used only in modules that packed into Java
    layout.withoutProjectLibrary("jps-javac-extension")
    layout.withoutProjectLibrary("Eclipse")
    for (platformLayoutCustomizer in productLayout.platformLayoutCustomizers) {
      platformLayoutCustomizer.accept(layout, context)
    }

    val alreadyPackedModules = HashSet<String>()
    for (entry in productLayout.additionalPlatformJars.entrySet()) {
      jar(entry.key, entry.value, productLayout, layout)
      alreadyPackedModules.addAll(entry.value)
    }

    for (moduleName in PLATFORM_API_MODULES) {
      // intellij.platform.core is used in Kotlin and Scala JPS plugins (PathUtil) https://youtrack.jetbrains.com/issue/IDEA-292483
      if (!productLayout.excludedModuleNames.contains(moduleName) && moduleName != "intellij.platform.core") {
        layout.withModule(moduleName, if (moduleName == "intellij.platform.jps.model") "jps-model.jar" else BaseLayout.APP_JAR)
      }
    }
    jar(BaseLayout.APP_JAR, productLayout.productApiModules, productLayout, layout)

    for (module in productLayout.productImplementationModules) {
      if (!productLayout.excludedModuleNames.contains(module) && !alreadyPackedModules.contains(module)) {
        val isRelocated = module == "intellij.xml.dom.impl" ||
                          module == "intellij.platform.structuralSearch" ||
                          // todo why intellij.tools.testsBootstrap is included to RM?
                          module == "intellij.tools.testsBootstrap" ||
                          module == "intellij.platform.duplicates.analysis"
        if (isRelocated) {
          layout.withModule(module, BaseLayout.APP_JAR)
        }
        else if (!context.productProperties.useProductJar || module.startsWith("intellij.platform.commercial")) {
          layout.withModule(module, productLayout.mainJarName)
        }
        else {
          layout.withModule(module, PRODUCT_JAR)
        }
      }
    }

    for ((module, patterns) in productLayout.moduleExcludes) {
      layout.excludeFromModule(module, patterns)
    }

    jar(UTIL_RT_JAR, listOf(
      "intellij.platform.util.rt",
      "intellij.platform.util.trove",
    ), productLayout, layout)

    jar(UTIL_JAR, listOf(
      "intellij.platform.util.rt.java8",
      "intellij.platform.util.zip",
      "intellij.platform.util.classLoader",
      "intellij.platform.util",
      "intellij.platform.util.text.matching",
      "intellij.platform.util.base",
      "intellij.platform.util.diff",
      "intellij.platform.util.xmlDom",
      "intellij.platform.util.jdom",
      "intellij.platform.extensions",
      "intellij.platform.tracing.rt",
      "intellij.platform.core",
      // GeneralCommandLine is used by Scala in JPS plugin
      "intellij.platform.ide.util.io",
      "intellij.platform.boot",
    ), productLayout, layout)

    jar("externalProcess-rt.jar", listOf(
      "intellij.platform.externalProcessAuthHelper.rt"
    ), productLayout, layout)

    jar(BaseLayout.APP_JAR, PLATFORM_IMPLEMENTATION_MODULES, productLayout, layout)
    // util.jar is loaded by JVM classloader as part of loading our custom PathClassLoader class - reduce file size
    jar(BaseLayout.APP_JAR, listOf(
      "intellij.platform.bootstrap",

      "intellij.platform.util.ui",
      "intellij.platform.util.ex",
      "intellij.platform.ide.util.io.impl",
      "intellij.platform.ide.util.netty",

      "intellij.relaxng",
      "intellij.json",
      "intellij.spellchecker",
      "intellij.platform.webSymbols",
      "intellij.xml.analysis.impl",
      "intellij.xml.psi.impl",
      "intellij.xml.structureView.impl",
      "intellij.xml.impl",

      "intellij.platform.vcs.impl",
      "intellij.platform.vcs.dvcs.impl",
      "intellij.platform.vcs.log.graph.impl",
      "intellij.platform.vcs.log.impl",

      "intellij.platform.collaborationTools",
      "intellij.platform.collaborationTools.auth",

      "intellij.platform.markdown.utils",

      "intellij.platform.icons",
      "intellij.platform.resources",
      "intellij.platform.resources.en",
      "intellij.platform.colorSchemes",
    ), productLayout, layout)

    jar("stats.jar", listOf(
      "intellij.platform.statistics",
      "intellij.platform.statistics.uploader",
      "intellij.platform.statistics.config",
    ), productLayout, layout)

    addModule("intellij.platform.statistics.devkit", productLayout, layout)
    addModule("intellij.platform.objectSerializer.annotations", productLayout, layout)
    if (!productLayout.excludedModuleNames.contains("intellij.java.guiForms.rt")) {
      layout.withModule("intellij.java.guiForms.rt", "forms_rt.jar")
    }

    addModule("intellij.platform.jps.model.serialization", "jps-model.jar", productLayout, layout)
    addModule("intellij.platform.jps.model.impl", "jps-model.jar", productLayout, layout)

    addModule("intellij.platform.externalSystem.rt", "external-system-rt.jar", productLayout, layout)

    addModule("intellij.platform.cdsAgent", "cds/classesLogAgent.jar", productLayout, layout)

    if (hasPlatformCoverage) {
      addModule("intellij.platform.coverage", BaseLayout.APP_JAR, productLayout, layout)
    }

    for (libraryName in productLayout.projectLibrariesToUnpackIntoMainJar) {
      layout.withProjectLibraryUnpackedIntoJar(libraryName, productLayout.mainJarName)
    }

    val productPluginSourceModuleName = context.productProperties.applicationInfoModule
    for (name in getProductPluginContentModules(context, productPluginSourceModuleName)) {
      layout.withModule(name, BaseLayout.APP_JAR)
    }

    layout.projectLibrariesToUnpack.putValue(UTIL_RT_JAR, "ion")

    for (item in additionalProjectLevelLibraries) {
      val name = item.libraryName
      if (!productLayout.projectLibrariesToUnpackIntoMainJar.contains(name) &&
          !layout.projectLibrariesToUnpack.values().contains(name) &&
          !layout.excludedProjectLibraries.contains(name)) {
        layout.includedProjectLibraries.add(item)
      }
    }
    layout.collectProjectLibrariesFromIncludedModules(context) { lib, module ->
      val name = lib.name
      if (module.name == "intellij.platform.buildScripts.downloader" && (name == "zstd-jni" || name == "zstd-jni-windows-aarch64")) {
        return@collectProjectLibrariesFromIncludedModules
      }

      layout.includedProjectLibraries
        .addOrGet(ProjectLibraryData(name, CUSTOM_PACK_MODE.getOrDefault(name, LibraryPackMode.MERGED)))
        .dependentModules.computeIfAbsent("core") { mutableListOf() }.add(module.name)
    }
    return layout
  }

  // result _must_ consistent, do not use Set.of or HashSet here
  fun getProductPluginContentModules(buildContext: BuildContext, productPluginSourceModuleName: String): Set<String> {
    var file = buildContext.findFileInModuleSources(productPluginSourceModuleName, "META-INF/plugin.xml")
    if (file == null) {
      file = buildContext.findFileInModuleSources(productPluginSourceModuleName,
                                                  "META-INF/${buildContext.productProperties.platformPrefix}Plugin.xml")
      if (file == null) {
        buildContext.messages.warning("Cannot find product plugin descriptor in '$productPluginSourceModuleName' module")
        return emptySet()
      }
    }

    Files.newInputStream(file).use {
      val contentList = DocumentBuilderFactory.newDefaultInstance()
        .newDocumentBuilder()
        .parse(it, file.toString())
        .documentElement
        .getElementsByTagName("content")
      if (contentList.length == 0) {
        return emptySet()
      }

      val modules = (contentList.item(0) as Element).getElementsByTagName("module")
      val result = LinkedHashSet<String>(modules.length)
      for (i in 0 until modules.length) {
        result.add((modules.item(i) as Element).getAttribute("name"))
      }
      return result
    }
  }
}