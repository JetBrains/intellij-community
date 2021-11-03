// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

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

import static org.jetbrains.intellij.build.impl.ProjectLibraryData.PackMode

@CompileStatic
final class PlatformModules {
  /**
   * List of modules which are included into lib/platform-api.jar in all IntelliJ based IDEs.
   */
  static final List<String> PLATFORM_API_MODULES = List.of(
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
    "intellij.platform.lang.core",
    "intellij.platform.lang",
    "intellij.platform.lvcs",
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
    "intellij.xml.analysis",
    "intellij.xml",
    "intellij.xml.psi",
    "intellij.xml.structureView",
    "intellij.platform.concurrency",
    )

  /**
   * List of modules which are included into lib/platform-impl.jar in all IntelliJ based IDEs.
   */
  static final List<String> PLATFORM_IMPLEMENTATION_MODULES = List.of(
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
    "intellij.platform.inspect",
    "intellij.platform.lang.impl",
    "intellij.platform.workspaceModel.storage",
    "intellij.platform.workspaceModel.jps",
    "intellij.platform.lvcs.impl",
    "intellij.platform.vfs.impl",
    "intellij.platform.ide.impl",
    "intellij.platform.projectModel.impl",
    "intellij.platform.macro",
    "intellij.platform.execution.impl",
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
    "intellij.platform.credentialStore.ui",
    "intellij.platform.rd.community",
    "intellij.platform.ml.impl",
    "intellij.remoteDev.util",
    "intellij.platform.feedback",
    )

  private static final String UTIL_JAR = "util.jar"

  static jar(String relativeJarPath,
             Collection<String> moduleNames,
             ProductModulesLayout productLayout,
             PlatformLayout layout) {
    for (String moduleName : moduleNames) {
      if (!productLayout.excludedModuleNames.contains(moduleName)) {
        layout.withModule(moduleName, relativeJarPath)
      }
    }
  }

  static addModule(String moduleName, ProductModulesLayout productLayout, PlatformLayout layout) {
    if (!productLayout.excludedModuleNames.contains(moduleName)) {
      layout.withModule(moduleName)
    }
  }

  static addModule(String moduleName, String relativeJarPath, ProductModulesLayout productLayout, PlatformLayout layout) {
    if (!productLayout.excludedModuleNames.contains(moduleName)) {
      layout.withModule(moduleName, relativeJarPath)
    }
  }

  static PlatformLayout createPlatformLayout(ProductModulesLayout productLayout,
                                             Set<String> allProductDependencies,
                                             List<JpsLibrary> additionalProjectLevelLibraries,
                                             BuildContext buildContext) {
    PlatformLayout layout = new PlatformLayout()
    // used only in modules that packed into Java
    layout.excludedProjectLibraries.add("jps-javac-extension")
    layout.excludedProjectLibraries.add("Eclipse")
    productLayout.platformLayoutCustomizer.accept(layout)

    Set<String> alreadyPackedModules = new HashSet<>()
    for (Map.Entry<String, Collection<String>> entry in productLayout.additionalPlatformJars.entrySet()) {
      jar(entry.key, entry.value, productLayout, layout)
      alreadyPackedModules.addAll(entry.value)
    }

    jar("platform-api.jar", PLATFORM_API_MODULES, productLayout, layout)
    jar("openapi.jar", productLayout.productApiModules, productLayout, layout)

    for (String module in productLayout.productImplementationModules) {
      if (!productLayout.excludedModuleNames.contains(module) && !alreadyPackedModules.contains(module)) {
        boolean isRelocated = module == "intellij.xml.dom.impl" ||
                              module == "intellij.platform.structuralSearch" ||
                              module == "intellij.platform.duplicates.analysis"
        layout.withModule(module, isRelocated ? BaseLayout.PLATFORM_JAR : productLayout.mainJarName)
      }
    }

    for (Map.Entry<String, Collection<String>> entry in productLayout.moduleExcludes.entrySet()) {
      layout.moduleExcludes.putValues(entry.key, entry.value)
    }

    jar(UTIL_JAR, List.of(
      "intellij.platform.util.rt",
      "intellij.platform.util.zip",
      "intellij.platform.util.classLoader",
      "intellij.platform.util",
      "intellij.platform.util.text.matching",
      "intellij.platform.util.base",
      "intellij.platform.util.xmlDom",
      "intellij.platform.util.ui",
      "intellij.platform.util.ex",
      "intellij.platform.ide.util.io",
      "intellij.platform.ide.util.io.impl",
      "intellij.platform.ide.util.netty",
      "intellij.platform.extensions",
      "intellij.platform.tracing.rt"
      ), productLayout, layout)

    jar(BaseLayout.PLATFORM_JAR, PLATFORM_IMPLEMENTATION_MODULES, productLayout, layout)
    jar(BaseLayout.PLATFORM_JAR, List.of(
      "intellij.relaxng",
      "intellij.json",
      "intellij.spellchecker",
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

      "intellij.platform.icons",
      "intellij.platform.resources",
      "intellij.platform.resources.en",
      "intellij.platform.colorSchemes",
      ), productLayout, layout)

    jar("stats.jar", List.of(
      "intellij.platform.statistics",
      "intellij.platform.statistics.uploader",
      "intellij.platform.statistics.config",
      ), productLayout, layout)

    jar("bootstrap.jar", List.of(
      "intellij.platform.bootstrap",
      "intellij.platform.boot"
    ), productLayout, layout)

    layout.excludedModuleLibraries.putValue("intellij.platform.credentialStore", "dbus-java")

    addModule("intellij.platform.statistics.devkit", productLayout, layout)
    addModule("intellij.platform.objectSerializer.annotations", productLayout, layout)
    addModule("intellij.java.guiForms.rt", productLayout, layout)

    addModule("intellij.platform.jps.model.serialization", "jps-model.jar", productLayout, layout)
    addModule("intellij.platform.jps.model.impl", "jps-model.jar", productLayout, layout)

    addModule("intellij.platform.externalSystem.rt", "external-system-rt.jar", productLayout, layout)

    addModule("intellij.platform.cdsAgent", "cds/classesLogAgent.jar", productLayout, layout)

    if (allProductDependencies.contains("intellij.platform.coverage")) {
      addModule("intellij.platform.coverage", BaseLayout.PLATFORM_JAR, productLayout, layout)
    }

    for (String libraryName in productLayout.projectLibrariesToUnpackIntoMainJar) {
      layout.withProjectLibraryUnpackedIntoJar(libraryName, productLayout.mainJarName)
    }

    String productPluginSourceModuleName = buildContext.productProperties.applicationInfoModule
    if (productPluginSourceModuleName != null) {
      List<String> modules = getProductPluginContentModules(buildContext, productPluginSourceModuleName)
      if (modules != null) {
        for (String name : modules) {
          layout.withModule(name, BaseLayout.PLATFORM_JAR)
        }
      }
    }

    Map<String, PackMode> customPackMode = Map.of(
      // jna uses native lib
      "jna", PackMode.STANDALONE_MERGED,
      "jetbrains-annotations-java5", PackMode.STANDALONE_SEPARATE_WITHOUT_VERSION_NAME,
      "intellij-coverage", PackMode.STANDALONE_SEPARATE,
    )

    layout.projectLibrariesToUnpack.putValues(UTIL_JAR, List.of(
      "JDOM",
      "Trove4j",
    ))

    for (JpsLibrary library in additionalProjectLevelLibraries) {
      String name = library.name
      if (!productLayout.projectLibrariesToUnpackIntoMainJar.contains(name) &&
          !layout.projectLibrariesToUnpack.values().contains(name) &&
          !layout.excludedProjectLibraries.contains(name)) {
        layout.withProjectLibrary(name, customPackMode.getOrDefault(name, PackMode.MERGED))
      }
    }

    Set<JpsLibrary> librariesToInclude = layout.computeProjectLibrariesFromIncludedModules(buildContext).keySet()
    for (JpsLibrary library in librariesToInclude) {
      String name = library.name
      layout.withProjectLibrary(name, customPackMode.getOrDefault(name, PackMode.MERGED))
    }
    return layout
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