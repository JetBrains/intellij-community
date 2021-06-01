// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.kotlin

import com.intellij.openapi.util.Couple
import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.impl.BuildContextImpl
import org.jetbrains.jps.model.artifact.JpsArtifact
import org.jetbrains.jps.model.library.JpsRepositoryLibraryType

@CompileStatic
enum KotlinPluginKind {
  IJ("KotlinPlugin", "kotlin.plugin.version"),
  AS("KotlinPluginCommunity", "kotlin.plugin.version.as"),
  IJ_CE("KotlinPluginCommunity", "kotlin.plugin.version"),
  MI("MobilePlugin",
     "kotlin.plugin.version",
     "intellij.mobile.ide",
     "mobile-ide/resources",
     [
       Couple.of("artifacts/kotlin-ocswift", "kotlin-ocswift"),
       Couple.of("artifacts/MobilePlugin", "Mobile")
     ]),
  AC_KMM(
    "AppCodeKMMPlugin",
    "kotlin.plugin.version",
    "kotlin-ultimate.appcode-kmm",
    "CIDR-appcode/appcode-kmm/resources"
  ),
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
         [Couple.of("${name}_zip/${name}.zip" as String, "kotlin-plugin-${getVersionFromProperty(versionPropertyName)}.zip" as String)],
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
    def buildNumber = buildNumber(context)
    def version = System.getProperty(versionPropertyName, version(context, buildNumber))
    def sinceBuild = System.getProperty("build.since", buildNumber)
    def untilBuild = System.getProperty("build.until", buildNumber)
    return new KotlinPluginArtifact(context).build(this, sinceBuild, untilBuild, version)
  }

  private static String buildNumber(CompilationContext context) {
    def buildNumber = context.options.buildNumber
    if (buildNumber == null || !buildNumber.contains('.')) {
      buildNumber = BuildContextImpl.readSnapshotBuildNumber(context.paths.communityHomeDir)
    }
    return buildNumber
  }

  private static String version(CompilationContext context, String rawBuildNumber) {
    def index = rawBuildNumber.indexOf('.')
    assert index > 0
    def intellijMajorVersion = rawBuildNumber.substring(0, index)
    def buildNumber = rawBuildNumber.substring(++index)
    def kotlinVersion = context.project.libraryCollection.libraries
                          .find { it.name.startsWith("kotlinc.") && it.type instanceof JpsRepositoryLibraryType }
                          ?.asTyped(JpsRepositoryLibraryType.INSTANCE)
                          ?.properties?.data?.version ?: "UNKNOWN"
    return "$intellijMajorVersion-$kotlinVersion-IJ$buildNumber"
  }

  private static String getVersionFromProperty(String versionPropertyName) {
    return System.getProperty(versionPropertyName) ?: "TC must specify '$versionPropertyName' property"
  }
}
