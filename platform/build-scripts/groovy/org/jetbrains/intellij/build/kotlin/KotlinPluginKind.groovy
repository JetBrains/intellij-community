// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.kotlin

import com.intellij.openapi.util.Couple
import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.impl.BuildContextImpl
import org.jetbrains.jps.model.artifact.JpsArtifact

@CompileStatic
enum KotlinPluginKind {
  IJ("KotlinPlugin", "kotlin.plugin.version"),
  AS("KotlinPluginCommunity", "kotlin.plugin.version.as"),
  IJ_CE("KotlinPluginCommunity", "kotlin.plugin.version"),
  MI("MobilePlugin",
     "kotlin.plugin.version",
     "kotlin-ultimate.mobile-native",
     "mobile-ide/mobile-native/resources",
     [
       Couple.of("artifacts/kotlin-android-extensions", "kotlin-android-extensions"),
       Couple.of("artifacts/kotlin-ocswift", "kotlin-ocswift"),
       Couple.of("artifacts/MobilePlugin", "Mobile")
     ]),
  AC("AppCodeKotlinPlugin",
     "kotlin.plugin.version",
     "kotlin-ultimate.appcode-native",
     "CIDR-appcode/appcode-kotlin-native/resources"),
  ROBOSCOPE(
    "RoboscopePlugin.zip", "kotlin.plugin.version",
    "util.android-studio.android-studio-roboscope-plugin",
    "plugins/kotlin/tools/android-studio/android-studio-roboscope-plugin/resources",
    [
      Couple.of("RoboscopePlugin_zip/RoboscopePlugin.zip", "roboscope-plugin.zip")
    ]),

  final String jpsArtifactIdentifier
  final String pluginXmlModuleName
  final String pluginXmlContentRoot
  final List<Couple<String>> artifactCopyRules
  private final String versionPropertyName
  final String version

  private KotlinPluginKind(String jpsArtifactIdentifier,
                           String pluginXmlModuleName,
                           String pluginXmlContentRoot,
                           List<Couple<String>> artifactCopyRules,
                           String versionPropertyName) {
    this.jpsArtifactIdentifier = jpsArtifactIdentifier
    this.pluginXmlModuleName = pluginXmlModuleName
    this.pluginXmlContentRoot = pluginXmlContentRoot
    this.versionPropertyName = versionPropertyName
    this.version = getVersionFromProperty(versionPropertyName)
    this.artifactCopyRules = artifactCopyRules
  }

  KotlinPluginKind(String name, String versionPropertyName) {
    this(name + ".zip", "kotlin.idea", "community/plugins/kotlin/resources-descriptors",
         [Couple.of("${name}_zip/${name}.zip", "kotlin-plugin-${getVersionFromProperty(versionPropertyName)}.zip")],
         versionPropertyName
    )
  }

  KotlinPluginKind(String name, String versionPropertyName, String pluginXmlModuleName, String pluginXmlContentRoot, List<Couple<String>> artifactCopyRules) {
    this(name, pluginXmlModuleName, pluginXmlContentRoot, artifactCopyRules, versionPropertyName)
  }

  KotlinPluginKind(String name, String versionPropertyName, String pluginXmlModuleName, String pluginXmlContentRoot) {
    this(name, pluginXmlModuleName, pluginXmlContentRoot, [Couple.of(name, name)], versionPropertyName)
  }

  JpsArtifact build(CompilationContext context) {
    def buildNumber = context.options.buildNumber ?: BuildContextImpl.readSnapshotBuildNumber(context.paths.communityHomeDir)
    def version = System.getProperty(versionPropertyName, buildNumber)
    def sinceBuild = System.getProperty("build.since", buildNumber)
    def untilBuild = System.getProperty("build.until", buildNumber)
    return new KotlinPluginArtifact(context).build(this, sinceBuild, untilBuild, version)
  }

  private static String getVersionFromProperty(String versionPropertyName) {
    return System.getProperty(versionPropertyName) ?: "TC must specify '$versionPropertyName' property"
  }
}
