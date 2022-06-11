package com.intellij.tools.launch

import java.io.File
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.SystemProperties
import org.jetbrains.intellij.build.dependencies.TeamCityHelper

interface PathsProvider {
  val productId: String
  val sourcesRootFolder: File
  val communityRootFolder: File
  val outputRootFolder: File

  val tempFolder: File
    get() = TeamCityHelper.getTempDirectory()?.toFile() ?: sourcesRootFolder.resolve("out").resolve("tmp")

  val launcherFolder: File
    get() = tempFolder.resolve("launcher").resolve(productId)

  val logFolder: File
    get() = launcherFolder.resolve("log")

  val configFolder: File
    get() = launcherFolder.resolve("config")

  val systemFolder: File
    get() = launcherFolder.resolve("system")

  val javaHomeFolder: File
    get() = File(SystemProperties.getJavaHome())

  val mavenRepositoryFolder: File
    get() = File(System.getProperty("user.home")).resolve(".m2/repository")

  val communityBinFolder: File
    get() = communityRootFolder.resolve("bin")

  val javaExecutable: File
    get() = when {
      SystemInfo.isWindows -> javaHomeFolder.resolve("bin").resolve("java.exe")
      else -> javaHomeFolder.resolve("bin").resolve("java")
    }

  val dockerVolumesToWritable: Map<File, Boolean>
    get() = emptyMap()
}