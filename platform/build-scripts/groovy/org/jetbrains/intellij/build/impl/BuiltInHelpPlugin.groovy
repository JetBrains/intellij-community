// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.jetbrains.intellij.build.BuildContext

@CompileStatic
final class BuiltInHelpPlugin {
  @CompileStatic(TypeCheckingMode.SKIP)
  static PluginLayout helpPlugin(BuildContext buildContext, String pluginVersion) {
    def productName = buildContext.applicationInfo.productName
    def moduleName = "intellij.platform.builtInHelp"
    def helpRoot = "${buildContext.paths.projectHome}/help"
    def resourceRoot = "$helpRoot/plugin-resources"
    if (!new File(resourceRoot, "topics/app.js").exists()) {
      buildContext.messages.warning("Skipping $productName Help plugin because $resourceRoot/topics/app.js not present")
      return null
    }
    return PluginLayout.plugin(moduleName) {
      def productLowerCase = productName.replace(" ", "-").toLowerCase()
      mainJarName = "$productLowerCase-help.jar"
      directoryName = "${productName.replace(" ", "")}Help"
      excludeFromModule(moduleName, "com/jetbrains/builtInHelp/indexer/**")
      doNotCopyModuleLibrariesAutomatically(["jsoup"])
      withGeneratedResources({ BuildContext context ->
        def helpModule = context.findRequiredModule(moduleName)
        def ant = context.ant
        context.messages.block("Indexing help topics..") {
          ant.java(classname: "com.jetbrains.builtInHelp.indexer.HelpIndexer", fork: true, failonerror: true) {
            jvmarg(line: "-ea -Xmx500m")
            sysproperty(key: "java.awt.headless", value: true)
            arg(path: "$resourceRoot/search")
            arg(path: "$resourceRoot/topics")

            classpath() {
              context.getModuleRuntimeClasspath(helpModule, false).each {
                pathelement(location: it)
              }
            }
          }
        }
        def patchedRoot = "$buildContext.paths.temp/patched-plugin-xml/$moduleName"
        new File(patchedRoot, "META-INF/services").mkdirs()
        new File(patchedRoot, "META-INF/services/org.apache.lucene.codecs.Codec").text =
          "org.apache.lucene.codecs.lucene50.Lucene50Codec"
        new File(patchedRoot, "META-INF/plugin.xml").text = pluginXml(context, pluginVersion)

        def jarName = "$buildContext.paths.temp/help/$productLowerCase-assets.jar"
        ant.jar(destfile: jarName) {
          ant.fileset(dir: resourceRoot) {
            ant.include(name: "topics/**")
            ant.include(name: "images/**")
            ant.include(name: "search/**")
          }
        }

        new File(jarName) }, "lib")
    }
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