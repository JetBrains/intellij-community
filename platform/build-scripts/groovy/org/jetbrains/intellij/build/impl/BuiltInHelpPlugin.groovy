// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl


import org.jetbrains.intellij.build.BuildContext

final class BuiltInHelpPlugin {

  static PluginLayout helpPlugin(BuildContext buildContext) {
    def productName = buildContext.applicationInfo.productName
    def moduleName = "intellij.platform.builtInHelp"
    def helpRoot = "${buildContext.paths.projectHome}/help"
    def indexerJar = "$helpRoot/indexer/bundled-help-indexer-1.0.jar"
    def resourceRoot = "$helpRoot/plugin-resources"
    if (!new File(indexerJar).exists()) {
      buildContext.messages.warning("Skipping ${productName}Help plugin because $indexerJar not present")
      return null
    }
    return PluginLayout.plugin(moduleName) {
      mainJarName = "${productName.toLowerCase()}-help.jar"
      directoryName = "${productName}Help"
      withGeneratedResources({ BuildContext context ->
        def helpModule = context.findRequiredModule(moduleName)
        def ant = context.ant
        context.messages.block("Indexing help topics..") {
          ant.java(classname: "com.jetbrains.bundled.help.search.HelpIndex", fork: true, failonerror: true) {
            jvmarg(line: "-ea -Xmx500m")
            sysproperty(key: "java.awt.headless", value: true)
            arg(path: "$resourceRoot/search")
            arg(path: "$resourceRoot/topics")

            classpath() {
              context.getModuleRuntimeClasspath(helpModule, false).each {
                pathelement(location: it)
              }
              pathelement(location: indexerJar)
            }
          }
        }
        def patchedRoot = "$buildContext.paths.temp/patched-plugin-xml/$moduleName"
        new File(patchedRoot, "META-INF/services").mkdirs()
        new File(patchedRoot, "META-INF/services/org.apache.lucene.codecs.Codec").text =
          "org.apache.lucene.codecs.lucene50.Lucene50Codec"
        new File(patchedRoot, "META-INF/plugin.xml").text = pluginXml(context)

        def jarName = "$buildContext.paths.temp/help/${productName.toLowerCase()}-assets.jar"
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

  static String pluginXml(BuildContext buildContext) {
    def version = buildContext.buildNumber;
    def productName = buildContext.applicationInfo.productName
    def pluginId = "bundled-${productName.toLowerCase()}-help"
    def pluginName = "${productName} Help"

    return """<idea-plugin allow-bundled-update="true">
    <name>$pluginName</name>
    <id>$pluginId</id>
    <version>$version</version>
    <idea-version since-build="${version.substring(0, version.lastIndexOf('.'))}"/>
    <vendor>JetBrains</vendor>
    <description>Contains $productName Web Help for offline use.</description>

    <depends>com.intellij.modules.${productName.toLowerCase()}</depends>

    <extensions defaultExtensionNs="com.intellij">
        <applicationService serviceInterface="com.intellij.openapi.help.HelpManager" overrides="true"
                            serviceImplementation="com.jetbrains.builtInHelp.BuiltInHelpManager" order="last"/>
        <httpRequestHandler implementation="com.jetbrains.builtInHelp.HelpSearchRequestHandler"/>
        <httpRequestHandler implementation="com.jetbrains.builtInHelp.HelpContentRequestHandler"/>
        <applicationConfigurable instance="com.jetbrains.builtInHelp.settings.SettingsPage"
                                 displayName="${productName} Help" groupId="tools"/>
    </extensions>
</idea-plugin>"""
  }
}