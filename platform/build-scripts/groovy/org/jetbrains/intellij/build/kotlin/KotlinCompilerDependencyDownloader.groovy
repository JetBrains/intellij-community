// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.kotlin

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.dependencies.BuildDependenciesManualRunOnly

import java.nio.file.Path

import static org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader.downloadFileToCacheLocation
import static org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader.extractFileToCacheLocation
import static org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader.getUriForMavenArtifact

@CompileStatic
class KotlinCompilerDependencyDownloader {
  private static final String MAVEN_REPOSITORY_URL = "https://cache-redirector.jetbrains.com/maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-ide-plugin-dependencies"
  private static final String ARTIFACT_GROUP_ID = "org.jetbrains.kotlin"

  static Path downloadAndExtractKotlinCompiler(BuildDependenciesCommunityRoot communityRoot) {
    def kotlinJpsPluginVersion = getKotlinJpsPluginVersion(communityRoot)
    URI kotlinDistUrl = getUriForMavenArtifact(MAVEN_REPOSITORY_URL, ARTIFACT_GROUP_ID, "kotlin-dist-for-ide", kotlinJpsPluginVersion, "jar")
    def kotlinDistJar = downloadFileToCacheLocation(communityRoot, kotlinDistUrl)
    return extractFileToCacheLocation(communityRoot, kotlinDistJar)
  }

  static Path downloadKotlinJpsPlugin(BuildDependenciesCommunityRoot communityRoot) {
    def kotlinJpsPluginVersion = getKotlinJpsPluginVersion(communityRoot)
    URI kotlinJpsPluginUrl = getUriForMavenArtifact(MAVEN_REPOSITORY_URL, ARTIFACT_GROUP_ID, "kotlin-jps-plugin-classpath", kotlinJpsPluginVersion, "jar")
    return downloadFileToCacheLocation(communityRoot, kotlinJpsPluginUrl)
  }

  private static String getKotlinJpsPluginVersion(BuildDependenciesCommunityRoot communityRoot) {
    def kotlinCompilerSettingsFile = communityRoot.getCommunityRoot().resolve(".idea/kotlinc.xml")
    def root = new XmlParser().parse(kotlinCompilerSettingsFile.toFile())
    Node kotlinJpsPluginSettingsTag = findNode(root, "component", "KotlinJpsPluginSettings")
    if (kotlinJpsPluginSettingsTag == null) {
      throw new IllegalStateException("KotlinJpsPluginSettings was not found in $kotlinCompilerSettingsFile")
    }
    def kotlinJpsPluginVersion = findNode(kotlinJpsPluginSettingsTag, "option", "version")?.attribute("value")
    if (kotlinJpsPluginVersion == null) {
      throw new IllegalStateException("KotlinJpsPlugin version was not found in $kotlinCompilerSettingsFile")
    }
    return kotlinJpsPluginVersion
  }

  private static Node findNode(Node parent, String tag, String name) {
    for (def child: parent.children()) {
      if (child instanceof Node && child.name() == tag && child.attribute("name") == name) {
        return child
      }
    }
    return null
  }

  static void main(String[] args) {
    Path path = downloadAndExtractKotlinCompiler(BuildDependenciesManualRunOnly.communityRootFromWorkingDirectory)
    println("Extracted Kotlin compiler is at " + path)
    Path jpsPath = downloadKotlinJpsPlugin(BuildDependenciesManualRunOnly.communityRootFromWorkingDirectory)
    println("Download Kotlin Jps Plugin at " + jpsPath)
  }
}
