// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.kotlin

import com.intellij.util.xml.dom.XmlElement
import com.intellij.util.xml.dom.readXmlAsModel
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader.*
import org.jetbrains.intellij.build.dependencies.BuildDependenciesManualRunOnly

import java.nio.file.Path

private const val MAVEN_REPOSITORY_URL = "https://cache-redirector.jetbrains.com/maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-ide-plugin-dependencies"
private const val ARTIFACT_GROUP_ID = "org.jetbrains.kotlin"

object KotlinCompilerDependencyDownloader {
  fun downloadAndExtractKotlinCompiler(communityRoot: BuildDependenciesCommunityRoot): Path {
    val kotlinJpsPluginVersion = getKotlinJpsPluginVersion(communityRoot)
    val kotlinDistUrl = getUriForMavenArtifact(MAVEN_REPOSITORY_URL, ARTIFACT_GROUP_ID, "kotlin-dist-for-ide", kotlinJpsPluginVersion, "jar")
    val kotlinDistJar = downloadFileToCacheLocation(communityRoot, kotlinDistUrl)
    return extractFileToCacheLocation(communityRoot, kotlinDistJar)
  }

  fun downloadKotlinJpsPlugin(communityRoot: BuildDependenciesCommunityRoot): Path {
    val kotlinJpsPluginVersion = getKotlinJpsPluginVersion(communityRoot)
    val kotlinJpsPluginUrl = getUriForMavenArtifact(MAVEN_REPOSITORY_URL, ARTIFACT_GROUP_ID, "kotlin-jps-plugin-classpath", kotlinJpsPluginVersion, "jar")
    return downloadFileToCacheLocation(communityRoot, kotlinJpsPluginUrl)
  }

  fun getKotlinJpsPluginVersion(communityRoot: BuildDependenciesCommunityRoot): String {
    val kotlinCompilerSettingsFile = communityRoot.communityRoot.resolve(".idea/kotlinc.xml")
    val root = readXmlAsModel(kotlinCompilerSettingsFile)
    val kotlinJpsPluginSettingsTag = findNode(root, "component", "KotlinJpsPluginSettings")
                                     ?: throw IllegalStateException("KotlinJpsPluginSettings was not found in $kotlinCompilerSettingsFile")
    return findNode(kotlinJpsPluginSettingsTag, "option", "version")?.getAttributeValue("value")
           ?: throw IllegalStateException("KotlinJpsPlugin version was not found in $kotlinCompilerSettingsFile")
  }

  private fun findNode(parent: XmlElement, tag: String, name: String): XmlElement? {
    return parent.children(tag).firstOrNull { it.getAttributeValue("name") == name }
  }

  @JvmStatic
  fun main(args: Array<String>) {
    val path = downloadAndExtractKotlinCompiler(BuildDependenciesManualRunOnly.getCommunityRootFromWorkingDirectory())
    println("Extracted Kotlin compiler is at $path")
    val jpsPath = downloadKotlinJpsPlugin(BuildDependenciesManualRunOnly.getCommunityRootFromWorkingDirectory())
    println("Download Kotlin Jps Plugin at $jpsPath")
  }
}
