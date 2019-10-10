// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.cds

import com.intellij.diagnostic.VMOptions
import com.intellij.execution.process.OSProcessUtil
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.sun.tools.attach.VirtualMachine
import java.io.File
import java.lang.management.ManagementFactory
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

//TODO: report to FUS if we use AppCDS in the product
//TODO:  detect currently used hash
//TODO: compute hash sum of IDE version + plugins for AppCDS update
//TODO: notify to regenerate AppCDS
//TODO: cleanup stale files (Windows may lock files)

object CDSManager {
  private val LOG = Logger.getInstance(javaClass)

  val isValidEnv: Boolean
    get() {
      // The AppCDS (JEP 350) is only added in JDK10,
      // The closest JRE we ship/support is 11
      if (!SystemInfo.isJavaVersionAtLeast(11)) return false

      //We use AppCDS feature that require
      // patches to work in Windows and macOS
      if (!SystemInfo.isJetBrainsJvm) return false

      return true
    }

  val isRunningWithCDS: Boolean
    get() {
      return isValidEnv && currentCDSArchive != null
    }

  val canBuildOrUpdateCDS: Boolean
    get() {
      if (!isValidEnv) return false

      val paths = CDSPaths.current()
      return !paths.isSame(currentCDSArchive)
    }

  private val currentCDSArchive: File?
    get() {
      val arguments = ManagementFactory.getRuntimeMXBean().inputArguments

      val xShare = arguments.any { it == "-Xshare:auto" || it == "-Xshare:on" }
      if (!xShare) return null

      val key = "-XX:SharedArchiveFile="
      return arguments.firstOrNull { it.startsWith(key) }?.removePrefix(key)?.let(::File)
    }

  fun cleanupStaleCDSFiles(indicator: ProgressIndicator) {
    val paths = CDSPaths.current()
    val currentCDSPath = currentCDSArchive

    val files = paths.baseDir.listFiles() ?: arrayOf()
    if (files.isEmpty()) return

    indicator.text2 = "Removing old Class Data Sharing files..."
    for (path in files) {
      indicator.checkCanceled()

      if (path != currentCDSPath && !paths.isOurFile(path)) {
        FileUtil.delete(path)
      }
    }
  }

  fun installCDS(indicator: ProgressIndicator): CDSPaths? {
    val paths = CDSPaths.current()
    if (paths.isSame(currentCDSArchive)) {
      LOG.warn("CDS archive is already generated. Nothing to do")
      return null
    }

    if (paths.classesMarkerFile.isFile) {
      LOG.warn("CDS archive is already generated. The marker file exists")
      return null
    }
    paths.classesMarkerFile.writeText(Instant.now().toString())

    LOG.info("Starting generation of CDS archive to the ${paths.classesArchiveFile} and ${paths.classesArchiveFile} files")

    indicator.text2 = "Collecting classes list..."
    indicator.checkCanceled()

    val durationList = measureTimeMillis {
      try {
        val agentPath = run {
          val libPath = File(PathManager.getLibPath()) / "cds" / "classesLogAgent.jar"
          if (libPath.isFile) return@run libPath

          LOG.warn("Failed to find bundled CDS classes agent in $libPath")

          //consider local debug IDE case
          val probe = File(PathManager.getHomePath()) / "out" / "classes" / "artifacts" / "classesLogAgent_jar" / "classesLogAgent.jar"
          if (probe.isFile) return@run probe

          error("Failed to resolve path to the CDS agent")
        }

        val vm = VirtualMachine.attach(OSProcessUtil.getApplicationPid())
        try {
          vm.loadAgent(agentPath.path, "${paths.classesListFile},${paths.classesPathFile}")
        }
        finally {
          vm.detach()
        }
      }
      catch (t: Throwable) {
        LOG.warn("Failed to attach CDS Java Agent to the running IDE instance. ${t.message}", t)
        return null
      }
    }

    LOG.info("CDS classes file is generated in ${StringUtil.formatDuration(durationList)}")

    indicator.text2 = "Generating classes archive..."
    indicator.checkCanceled()

    val durationLink = measureTimeMillis {
      val ext = if (SystemInfo.isWindows) ".exe" else ""
      val javaExe = File(System.getProperty("java.home")!!) / "bin" / "java$ext"
      val args = listOf(
        javaExe.path,
        "-Xshare:dump",
        "-XX:+UnlockDiagnosticVMOptions",
        "-XX:SharedClassListFile=${paths.classesListFile}",
        "-XX:SharedArchiveFile=${paths.classesArchiveFile}",
        "-cp",
        "@${paths.classesPathFile}"
      )

      val cwd = File(".").canonicalFile
      LOG.info("Running CDS generation process: $args in $cwd")

      indicator.checkCanceled()
      val process = ProcessBuilder().command(args).inheritIO().directory(cwd).start()
      if (!process.waitFor(10, TimeUnit.MINUTES)) {
        LOG.warn("Failed to generate CDS archive, the process took too long and will be killed")
        process.destroyForcibly()
        return null
      }
    }

    LOG.warn("Generated CDS archive to ${paths.classesArchiveFile}, " +
             "size = ${StringUtil.formatFileSize(paths.classesArchiveFile.length())} " +
             "in ${StringUtil.formatDuration(durationLink)}")

    VMOptions.writeEnableCDSArchiveOption(paths.classesArchiveFile.absolutePath)

    return paths
  }

  fun removeCDS() {
    LOG.warn("CDS archive is disabled")
    VMOptions.writeDisableCDSArchiveOption()
  }

  private operator fun File.div(s: String) = File(this, s)
}
