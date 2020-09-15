package com.intellij.tools.launch

import java.io.File
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.SystemProperties

interface IntelliJProjectPathsProvider {
  val projectRootFolder: File
  val communityRootFolder: File get() = File(projectRootFolder, "community")
  val outputRootFolder: File get() = File(projectRootFolder, "out")
}

interface PathsProvider : IntelliJProjectPathsProvider {
  val productId: String
  override val projectRootFolder: File
  override val communityRootFolder: File
  override val outputRootFolder: File

  val tempFolder: File
    get() = TeamCityHelper.tempDirectory ?: projectRootFolder.resolve("out").resolve("tmp")

  val launcherFolder: File
    get() = tempFolder.resolve("launcher").resolve(productId)

  val logFolder: File
    get() = launcherFolder.resolve("log")

  val configFolder: File
    get() = launcherFolder.resolve("config")

  val systemFolder: File
    get() = launcherFolder.resolve("system")

  private val javaHomeFolder: File
    get() = File(SystemProperties.getJavaHome())

  val javaExecutable: File
    get() = when {
      SystemInfo.isWindows -> javaHomeFolder.resolve("bin").resolve("java.exe")
      else -> javaHomeFolder.resolve("bin").resolve("java")
    }
}