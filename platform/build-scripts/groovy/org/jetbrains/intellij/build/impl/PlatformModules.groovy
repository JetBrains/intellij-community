// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.ProductModulesLayout
import org.jetbrains.jps.model.library.JpsLibrary
import org.w3c.dom.Element
import org.w3c.dom.NodeList

import javax.xml.parsers.DocumentBuilderFactory
import java.nio.file.Files
import java.nio.file.Path

import static org.jetbrains.intellij.build.impl.BaseLayout.PLATFORM_JAR

@CompileStatic
final class PlatformModules {
  /**
   * List of modules which are included into lib/platform-api.jar in all IntelliJ based IDEs.
   */
  static List<String> PLATFORM_API_MODULES = List.of(
    "intellij.platform.analysis",
    "intellij.platform.builtInServer",
    "intellij.platform.core",
    "intellij.platform.diff",
    "intellij.platform.vcs.dvcs",
    "intellij.platform.editor",
    "intellij.platform.externalSystem",
    "intellij.platform.codeStyle",
    "intellij.platform.indexing",
    "intellij.platform.jps.model",
    "intellij.platform.lang",
    "intellij.platform.lvcs",
    "intellij.platform.ide",
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
    "intellij.platform.debugger",
    "intellij.xml.analysis",
    "intellij.xml",
    "intellij.xml.psi",
    "intellij.xml.structureView",
    "intellij.platform.concurrency",
  )

  /**
   * List of modules which are included into lib/platform-impl.jar in all IntelliJ based IDEs.
   */
  static List<String> PLATFORM_IMPLEMENTATION_MODULES = List.of(
    "intellij.platform.analysis.impl",
    "intellij.platform.builtInServer.impl",
    "intellij.platform.core.impl",
    "intellij.platform.diff.impl",
    "intellij.platform.editor.ex",
    "intellij.platform.codeStyle.impl",
    "intellij.platform.indexing.impl",
    "intellij.platform.elevation",
    "intellij.platform.elevation.client",
    "intellij.platform.elevation.common",
    "intellij.platform.elevation.daemon",
    "intellij.platform.execution.impl",
    "intellij.platform.inspect",
    "intellij.platform.lang.impl",
    "intellij.platform.workspaceModel.storage",
    "intellij.platform.workspaceModel.jps",
    "intellij.platform.lvcs.impl",
    "intellij.platform.ide.impl",
    "intellij.platform.projectModel.impl",
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
    "intellij.platform.core.ui",
    "intellij.platform.credentialStore",
    "intellij.platform.rd.community",
    "intellij.platform.ml.impl"
  )

  private static final String UTIL_JAR = "util.jar"

  @CompileDynamic
  static PlatformLayout createPlatformLayout(ProductModulesLayout productLayout,
                                             Set<String> allProductDependencies,
                                             List<JpsLibrary> additionalProjectLevelLibraries,
                                             BuildContext buildContext) {
    PlatformLayout.platform(productLayout.platformLayoutCustomizer) {
      BaseLayoutSpec.metaClass.addModule = { String moduleName ->
        if (!productLayout.excludedModuleNames.contains(moduleName)) {
          withModule(moduleName)
        }
      }
      BaseLayoutSpec.metaClass.addModule = { String moduleName, String relativeJarPath ->
        if (!productLayout.excludedModuleNames.contains(moduleName)) {
          withModule(moduleName, relativeJarPath)
        }
      }

      productLayout.additionalPlatformJars.entrySet().each {
        String jarName = it.key
        it.value.each {
          addModule(it, jarName)
        }
      }

      for (String module in PLATFORM_API_MODULES) {
        addModule(module, "platform-api.jar")
      }
      for (String module in PLATFORM_IMPLEMENTATION_MODULES) {
        addModule(module, PLATFORM_JAR)
      }
      for (String module in productLayout.productApiModules) {
        addModule(module, "openapi.jar")
      }

      for (String module in productLayout.productImplementationModules) {
        boolean isRelocated = module == "intellij.xml.dom.impl" ||
                              module == "intellij.platform.structuralSearch" ||
                              module == "intellij.platform.duplicates.analysis"
        addModule(module, isRelocated ? PLATFORM_JAR : productLayout.mainJarName)
      }

      productLayout.moduleExcludes.entrySet().each {
        layout.moduleExcludes.putValues(it.key, it.value)
      }

      addModule("intellij.platform.util", UTIL_JAR)
      addModule("intellij.platform.util.rt", UTIL_JAR)
      addModule("intellij.platform.util.zip", UTIL_JAR)
      addModule("intellij.platform.util.classLoader", UTIL_JAR)
      addModule("intellij.platform.util.text.matching", UTIL_JAR)
      addModule("intellij.platform.util.collections", UTIL_JAR)
      addModule("intellij.platform.util.strings", UTIL_JAR)
      addModule("intellij.platform.util.xmlDom", UTIL_JAR)
      addModule("intellij.platform.util.diagnostic", UTIL_JAR)
      addModule("intellij.platform.util.ui", UTIL_JAR)
      addModule("intellij.platform.util.ex", UTIL_JAR)
      addModule("intellij.platform.ide.util.io", UTIL_JAR)
      addModule("intellij.platform.extensions", UTIL_JAR)

      withoutModuleLibrary("intellij.platform.credentialStore", "dbus-java")
      addModule("intellij.platform.statistics", "stats.jar")
      addModule("intellij.platform.statistics.uploader", "stats.jar")
      addModule("intellij.platform.statistics.config", "stats.jar")
      addModule("intellij.platform.statistics.devkit")

      for (String module in List.of("intellij.relaxng",
                                    "intellij.json",
                                    "intellij.spellchecker",
                                    "intellij.xml.analysis.impl",
                                    "intellij.xml.psi.impl",
                                    "intellij.xml.structureView.impl",
                                    "intellij.xml.impl")) {
        addModule(module, PLATFORM_JAR)
      }

      addModule("intellij.platform.vcs.impl", PLATFORM_JAR)
      addModule("intellij.platform.vcs.dvcs.impl", PLATFORM_JAR)
      addModule("intellij.platform.vcs.log.graph.impl", PLATFORM_JAR)
      addModule("intellij.platform.vcs.log.impl", PLATFORM_JAR)
      addModule("intellij.platform.collaborationTools", PLATFORM_JAR)

      addModule("intellij.platform.objectSerializer.annotations")

      addModule("intellij.platform.bootstrap")
      addModule("intellij.java.guiForms.rt")
      addModule("intellij.platform.boot", "bootstrap.jar")

      addModule("intellij.platform.icons", PLATFORM_JAR)
      addModule("intellij.platform.resources", PLATFORM_JAR)
      addModule("intellij.platform.colorSchemes", PLATFORM_JAR)
      addModule("intellij.platform.resources.en", PLATFORM_JAR)

      addModule("intellij.platform.jps.model.serialization", "jps-model.jar")
      addModule("intellij.platform.jps.model.impl", "jps-model.jar")

      addModule("intellij.platform.externalSystem.rt", "external-system-rt.jar")

      addModule("intellij.platform.cdsAgent", "cds/classesLogAgent.jar")

      if (allProductDependencies.contains("intellij.platform.coverage")) {
        addModule("intellij.platform.coverage")
      }

      for (String libraryName in productLayout.projectLibrariesToUnpackIntoMainJar) {
        withProjectLibraryUnpackedIntoJar(libraryName, productLayout.mainJarName)
      }

      String productPluginSourceModuleName = buildContext.productProperties.applicationInfoModule
      if (productPluginSourceModuleName != null) {
        List<String> modules = getProductPluginContentModules(buildContext, productPluginSourceModuleName)
        if (modules != null) {
          for (String name : modules) {
            withModule(name, PLATFORM_JAR)
          }
        }
      }

      layout.projectLibrariesToUnpack.putValues(UTIL_JAR, List.of(
        "JDOM",
        "Trove4j",
        "aalto-xml",
        "netty-buffer",
        "netty-codec-http",
        "netty-handler-proxy",
        "Log4J",
        "fastutil-min",
      ))

      for (JpsLibrary library in additionalProjectLevelLibraries) {
        if (!productLayout.projectLibrariesToUnpackIntoMainJar.contains(library.name) &&
            !layout.projectLibrariesToUnpack.values().contains(library.name) &&
            !layout.excludedProjectLibraries.contains(library.name)) {
          withProjectLibrary(library.name)
        }
      }

      withProjectLibrariesFromIncludedModules(buildContext)

      for (String toRemoveVersion : List.of("jna", "jetbrains-annotations-java5")) {
        removeVersionFromProjectLibraryJarNames(toRemoveVersion)
      }
    }
  }

  private static @Nullable List<String> getProductPluginContentModules(@NotNull BuildContext buildContext,
                                                                       @NotNull String productPluginSourceModuleName) {
    Path file = buildContext.findFileInModuleSources(productPluginSourceModuleName, "META-INF/plugin.xml")
    if (file == null) {
      file = buildContext.findFileInModuleSources(productPluginSourceModuleName,
                                                  "META-INF/" + buildContext.productProperties.platformPrefix + "Plugin.xml")
      if (file == null) {
        buildContext.messages.warning("Cannot find product plugin descriptor in '$productPluginSourceModuleName' module")
        return null
      }
    }

    Files.newInputStream(file).withCloseable {
      NodeList contentList = DocumentBuilderFactory.newDefaultInstance().newDocumentBuilder()
        .parse(it, file.toString()).getDocumentElement().getElementsByTagName("content")
      if (contentList.length != 0) {
        NodeList modules = ((Element)contentList.item(0)).getElementsByTagName("module")
        List<String> result = new ArrayList<>(modules.length)
        for (int i = 0; i < modules.length; i++) {
          Element module = (Element)modules.item(i)
          result.add(module.getAttribute("name"))
        }
        return result
      }
      return null
    }
  }
}
