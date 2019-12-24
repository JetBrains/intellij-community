// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.cds

import com.google.common.hash.Hashing
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.SystemInfo
import java.io.File
import java.time.LocalDateTime


class CDSPaths private constructor(val baseDir: File,
                                   val dumpOutputFile: File,
                                   val cdsClassesHash: String) {
  val classesErrorMarkerFile = File(baseDir, "${cdsClassesHash}.error")
  val classesListFile = File(baseDir, "${cdsClassesHash}.txt")
  val classesPathFile = File(baseDir, "${cdsClassesHash}.classpath")
  val classesArchiveFile = File(baseDir, "${cdsClassesHash}.jsa")

  fun isSame(jsaFile: File?) = jsaFile != null && jsaFile == classesArchiveFile && jsaFile.isFile
  fun isOurFile(file: File?) = file == classesErrorMarkerFile || file == classesListFile || file == classesPathFile || file == classesArchiveFile

  fun mkdirs() {
    baseDir.mkdirs()
    dumpOutputFile.parentFile?.mkdirs()
  }

  fun markError(message: String) {
    classesErrorMarkerFile.writeText("Failed on ${LocalDateTime.now()}: $message")
  }

  // re-compute installed plugins cache and see if there plugins were not changed
  fun hasSameEnvironmentToBuildCDSArchive() = computePaths().cdsClassesHash == current.cdsClassesHash

  companion object {
    private val systemDir get() = File(PathManager.getSystemPath())

    val freeSpaceForCDS get() = systemDir.freeSpace

    val current: CDSPaths by lazy { computePaths() }

    private fun computePaths(): CDSPaths {
      val baseDir = File(systemDir, "cds")
      val hasher = Hashing.sha256().newHasher()

      // make sure the system folder was not moved
      hasher.putString(baseDir.absolutePath, Charsets.UTF_8)

      // make sure IDE folder was not moved
      hasher.putString(PathManager.getHomePath(), Charsets.UTF_8)

      val info = ApplicationInfo.getInstance()
      //IntelliJ product
      hasher.putString(info.build.asString(), Charsets.UTF_8)

      //java runtime
      hasher.putString(SystemInfo.getOsNameAndVersion(), Charsets.UTF_8)
      hasher.putString(SystemInfo.JAVA_RUNTIME_VERSION, Charsets.UTF_8)
      hasher.putString(SystemInfo.JAVA_VENDOR, Charsets.UTF_8)
      hasher.putString(SystemInfo.JAVA_VERSION, Charsets.UTF_8)

      //active plugins
      PluginManagerCore.getLoadedPlugins().sortedBy { it.pluginId.idString }.forEach {
        hasher.putString(it.pluginId.idString + ":" + it.version, Charsets.UTF_8)
      }

      val cdsClassesHash = "${info.build.asString()}-${hasher.hash()}"

      val dumpLog = File(PathManager.getLogPath(), "cds-dump.log")
      return CDSPaths(baseDir, dumpLog, cdsClassesHash)
    }
  }
}
