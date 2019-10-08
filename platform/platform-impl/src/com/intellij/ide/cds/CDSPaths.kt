// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.cds

import com.google.common.hash.Hashing
import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.SystemInfo
import java.io.File


class CDSPaths private constructor(baseDir: File, cdsClassesHash: String) {
  val classesListFile = File(baseDir, "${cdsClassesHash}.txt")
  val classesPathFile = File(baseDir, "${cdsClassesHash}.classpath")
  val classesArchiveFile = File(baseDir, "${cdsClassesHash}.jsa")

  fun isSame(jsaFile: File?) = jsaFile == classesArchiveFile

  companion object {
    val baseDir: File get() = File(PathManager.getSystemPath(), "cds").also { it.mkdirs() }

    fun current(): CDSPaths {
      val hasher = Hashing.sha256().newHasher()

      val info = ApplicationInfo.getInstance()
      //IntelliJ product
      hasher.putString(info.build.asString(), Charsets.UTF_8)

      //java runtime
      hasher.putString(SystemInfo.getOsNameAndVersion(), Charsets.UTF_8)
      hasher.putString(SystemInfo.JAVA_RUNTIME_VERSION, Charsets.UTF_8)
      hasher.putString(SystemInfo.JAVA_VENDOR, Charsets.UTF_8)
      hasher.putString(SystemInfo.JAVA_VERSION, Charsets.UTF_8)

      //active plugins
      PluginManager.getLoadedPlugins().sortedBy { it.pluginId.idString }.forEach {
        hasher.putString(it.pluginId.idString + ":" + it.version, Charsets.UTF_8)
      }

      val cdsClassesHash = "${info.build.asString()}-${hasher.hash()}"
      return CDSPaths(baseDir, cdsClassesHash)
    }
  }
}
