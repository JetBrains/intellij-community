// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.kotlin

import com.intellij.util.xml.dom.XmlElement
import com.intellij.util.xml.dom.readXmlAsModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader.downloadFileToCacheLocation
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader.extractFileToCacheLocation
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader.getTargetFile
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader.getUriForMavenArtifact
import org.jetbrains.intellij.build.retryWithExponentialBackOff
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.name

private const val MAVEN_REPOSITORY_URL = "https://cache-redirector.jetbrains.com/intellij-dependencies"
private const val ARTIFACT_GROUP_ID = "org.jetbrains.kotlin"

object KotlinCompilerDependencyDownloader {
  fun downloadAndExtractKotlinCompiler(communityRoot: BuildDependenciesCommunityRoot): Path {
    val kotlinJpsPluginVersion = getKotlinJpsPluginVersion(communityRoot)
    val kotlinDistUrl = getUriForMavenArtifact(MAVEN_REPOSITORY_URL, ARTIFACT_GROUP_ID, "kotlin-dist-for-ide", kotlinJpsPluginVersion, "jar")
    val kotlinDistJar = downloadFileToCacheLocation(communityRoot, kotlinDistUrl)
    return extractFileToCacheLocation(communityRoot, kotlinDistJar)
  }

  suspend fun downloadKotlinJpsPlugin(communityRoot: BuildDependenciesCommunityRoot): Path = withContext(Dispatchers.IO) {
    val kotlinJpsPluginVersion = getKotlinJpsPluginVersion(communityRoot)
    val kotlinJpsPluginUrl = getUriForMavenArtifact(MAVEN_REPOSITORY_URL, ARTIFACT_GROUP_ID, "kotlin-jps-plugin-classpath", kotlinJpsPluginVersion, "jar")

    val cacheLocation = getTargetFile(communityRoot, kotlinJpsPluginUrl.toString())
    if (cacheLocation.exists()) {
      return@withContext cacheLocation
    }

    // Download file by hand since calling entire ktor/cio/coroutines stuff *before* loading JPS plugin into classpath
    // leads to funny kotlin-reflect failures later in Kotlin JPS plugin
    // Ideal solution would be to move compilation to other process altogether and do not modify current process classpath
    println(" * Downloading $kotlinJpsPluginUrl")
    val tmpLocation = Files.createTempFile(cacheLocation.parent, cacheLocation.name, ".tmp")
    retryWithExponentialBackOff {
      kotlinJpsPluginUrl.toURL().openStream().use {
        Files.copy(it, tmpLocation, StandardCopyOption.REPLACE_EXISTING)
      }
    }
    Files.move(tmpLocation, cacheLocation, StandardCopyOption.ATOMIC_MOVE)
    return@withContext cacheLocation
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
}
