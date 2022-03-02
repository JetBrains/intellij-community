// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileStatic
import io.opentelemetry.api.trace.Span
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.tasks.HelpPluginKt

import java.nio.file.Files
import java.nio.file.Path
import java.util.function.BiConsumer

@CompileStatic
final class BuiltInHelpPlugin {
  private static final String MODULE_NAME = "intellij.platform.builtInHelp"

  static PluginLayout helpPlugin(BuildContext context, String pluginVersion) {
    String productName = context.applicationInfo.productName
    Path resourceRoot = context.paths.projectHomeDir.resolve("help/plugin-resources")
    if (Files.notExists(resourceRoot.resolve("topics/app.js"))) {
      Span.current().addEvent("skip $productName Help plugin because $resourceRoot/topics/app.js not present")
      return null
    }

    return PluginLayout.plugin(MODULE_NAME) {
      configure(delegate, pluginVersion, productName, resourceRoot)
    }
  }

  private static configure(PluginLayout.PluginLayoutSpec spec, String pluginVersion, String productName, Path resourceRoot) {
    String productLowerCase = productName.replace(" ", "-").toLowerCase()
    spec.mainJarName = "$productLowerCase-help.jar"
    spec.directoryName = "${productName.replace(" ", "")}Help"
    spec.excludeFromModule(MODULE_NAME, "com/jetbrains/builtInHelp/indexer/**")
    spec.doNotCopyModuleLibrariesAutomatically(List.of("jsoup"))
    spec.withGeneratedResources(new BiConsumer<Path, BuildContext>() {
      @Override
      void accept(Path targetDir, BuildContext context) {
        Path assetJar = targetDir.resolve("lib/help-$productLowerCase-assets.jar")
        HelpPluginKt.buildResourcesForHelpPlugin(
          resourceRoot,
          context.getModuleRuntimeClasspath(context.findRequiredModule(MODULE_NAME), false),
          assetJar,
          context.messages)
      }
    })
    spec.withPatch(new BiConsumer<ModuleOutputPatcher, BuildContext>() {
      @Override
      void accept(ModuleOutputPatcher patcher, BuildContext context) {
        patcher.patchModuleOutput(MODULE_NAME,
                                  "META-INF/services/org.apache.lucene.codecs.Codec",
                                  "org.apache.lucene.codecs.lucene50.Lucene50Codec")
        patcher.patchModuleOutput(MODULE_NAME, "META-INF/plugin.xml", pluginXml(context, pluginVersion), true)
      }
    })
  }

  private static String pluginXml(BuildContext buildContext, String version) {
    def productName = buildContext.applicationInfo.productName
    def productLowerCase = productName.replace(" ", "-").toLowerCase()
    def pluginId = "bundled-$productLowerCase-help"
    def pluginName = "$productName Help"
    def productModuleDep = "com.intellij.modules.${productLowerCase.replace("intellij-", "")}"

    return """<idea-plugin allow-bundled-update="true">
    <name>$pluginName</name>
    <id>$pluginId</id>
    <version>$version</version>
    <idea-version since-build="${version.substring(0, version.lastIndexOf('.'))}"/>
    <vendor>JetBrains</vendor>
    <description>$productName Web Help for offline use.</description>

    <depends>$productModuleDep</depends>
    <resource-bundle>messages.BuiltInHelpBundle</resource-bundle>

    <extensions defaultExtensionNs="com.intellij">
        <applicationService serviceInterface="com.intellij.openapi.help.HelpManager" overrides="true"
                            serviceImplementation="com.jetbrains.builtInHelp.BuiltInHelpManager" order="last"/>
        <httpRequestHandler implementation="com.jetbrains.builtInHelp.HelpSearchRequestHandler"/>
        <httpRequestHandler implementation="com.jetbrains.builtInHelp.HelpContentRequestHandler"/>
        <applicationConfigurable instance="com.jetbrains.builtInHelp.settings.SettingsPage"
                                 displayName="$productName Help" groupId="tools"/>
    </extensions>
</idea-plugin>"""
  }
}