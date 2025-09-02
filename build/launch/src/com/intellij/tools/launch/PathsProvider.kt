package com.intellij.tools.launch

import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.dependencies.JdkDownloader
import org.jetbrains.intellij.build.dependencies.TeamCityHelper
import java.io.File

interface PathsProvider {
  val productId: String
  val sourcesRootFolder: File
  val communityRootFolder: File
  val outputRootFolder: File

  val tempFolder: File
    get() = resolveTempFolder(sourcesRootFolder)

  val launcherFolder: File
    get() = tempFolder.resolve("launcher").resolve(productId)

  val logFolder: File
    get() = launcherFolder.resolve("log")

  val configFolder: File
    get() = launcherFolder.resolve("config")

  val systemFolder: File
    get() = launcherFolder.resolve("system")

  val javaHomeFolder: File
    get() = JdkDownloader.blockingGetJdkHomeAndLog(BuildDependenciesCommunityRoot(communityRootFolder.toPath())).normalize().toFile()

  val mavenRepositoryFolder: File
    get() = File(System.getProperty("user.home")).resolve(".m2/repository")

  val communityBinFolder: File
    get() = communityRootFolder.resolve("bin")

  val ultimateRootMarker: File
    get() = sourcesRootFolder.resolve(".ultimate.root.marker")

  val javaExecutable: File
    get() = JdkDownloader.getJavaExecutable(javaHomeFolder.toPath()).normalize().toFile()

  val dockerVolumesToWritable: Map<File, Boolean>
    get() = emptyMap()

  val pluginsFolder: File
    get() = configFolder.resolve("plugins")

  companion object {
    fun resolveTempFolder(sourcesRootFolder: File) = TeamCityHelper.tempDirectory?.toFile()
                                                     ?: sourcesRootFolder.resolve("out").resolve("tmp")
  }
}