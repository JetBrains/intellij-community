// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.cds

import com.google.common.hash.Hashing
import com.intellij.diagnostic.VMOptions
import com.intellij.execution.process.OSProcessUtil
import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil
import com.sun.tools.attach.VirtualMachine
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

//TODO: report to FUS if we use AppCDS in the product
//TODO:  detect currently used hash
//TODO: compute hash sum of IDE version + plugins for AppCDS update
//TODO: notify to regenerate AppCDS
//TODO: cleanup stale files (Windows may lock files)

object CDSArchiveManager {
  private val LOG = Logger.getInstance(javaClass)

  val isValidEnv by lazy {
    // The AppCDS (JEP 350) is only added in JDK10,
    // The closest JRE we ship/support is 11
    if (!SystemInfo.isJavaVersionAtLeast(11)) return@lazy false

    //We use AppCDS feature that require
    // patches to work in Windows and macOS
    if (!SystemInfo.isJetBrainsJvm) return@lazy false

    true
  }

  private val classesHash by lazy {
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

    return@lazy "${info.build.asString()}-${hasher.hash()}"
  }

  private val agentPath by lazy {
    val libPath = File(PathManager.getLibPath()) / "cds" / "classesLogAgent.jar"
    if (libPath.isFile) return@lazy libPath

    LOG.warn("Failed to find bundled CDS classes agent in $libPath")

    //consider local debug IDE case
    val probe = File(PathManager.getHomePath()) / "out" / "classes" / "artifacts" / "classesLogAgent_jar" / "classesLogAgent.jar"
    if (probe.isFile) return@lazy probe
    error("Failed to resolve path to the CDS agent")
  }

  fun installCDS(indicator: ProgressIndicator) {
    indicator.isIndeterminate = true

    val baseDir = File(PathManager.getSystemPath(), "cds")
    baseDir.mkdirs()

    //TODO: cleanup stale files
    val classesListFile = File(baseDir, "$classesHash.txt")
    val classesPathFile = File(baseDir, "$classesHash.classpath")
    val classesArchiveFile = File(baseDir, "$classesHash.jsa")

    LOG.info("Starting generation of CDS archive to the $classesListFile and $classesPathFile files")

    indicator.text2 = "Collecting classes list..."
    indicator.checkCanceled()

    val durationList = measureTimeMillis {
      try {
        val vm = VirtualMachine.attach(OSProcessUtil.getApplicationPid())
        try {
          vm.loadAgent(agentPath.path, "$classesListFile,$classesPathFile")
        }
        finally {
          vm.detach()
        }
      }
      catch (t: Throwable) {
        LOG.warn("Failed to attach CDS Java Agent to the running IDE instance. ${t.message}", t)
        return
      }
    }

    LOG.info("CDS classes file is generated in ${StringUtil.formatDuration(durationList)}")

    indicator.text2 = "Generating AppCDS classes archive..."
    indicator.checkCanceled()

    val durationLink = measureTimeMillis {
      val ext = if (SystemInfo.isWindows) ".exe" else ""
      val javaExe = File(System.getProperty("java.home")!!) / "bin" / "java$ext"
      val args = listOf(
        javaExe.path,
        "-Xshare:dump",
        "-XX:+UnlockDiagnosticVMOptions",
        "-XX:SharedClassListFile=$classesListFile",
        "-XX:SharedArchiveFile=$classesArchiveFile",
        "-cp",
        "@$classesPathFile"
      )

      val cwd = File(".").canonicalFile
      LOG.info("Running CDS generation process: $args in $cwd")

      indicator.checkCanceled()
      val process = ProcessBuilder().command(args).inheritIO().directory(cwd).start()
      if (!process.waitFor(10, TimeUnit.MINUTES)) {
        LOG.warn("Failed to generate CDS archive, the process took too long and will be killed")
        process.destroyForcibly()
        return
      }
    }

    LOG.warn("Generated CDS archive to $classesArchiveFile, size = ${StringUtil.formatFileSize(classesArchiveFile.length())} in ${StringUtil.formatDuration(durationLink)}")
    VMOptions.writeEnableCDSArchiveOption(classesArchiveFile.absolutePath)
  }

  fun removeCDS() {
    LOG.warn("CDS archive is disabled")
    VMOptions.writeDisableCDSArchiveOption()
  }
}

private operator fun File.div(s: String) = File(this, s)
