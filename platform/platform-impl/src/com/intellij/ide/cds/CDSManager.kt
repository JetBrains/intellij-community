// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.cds

import com.intellij.diagnostic.VMOptions
import com.intellij.execution.process.OSProcessUtil
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.projectRoots.JdkUtil
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.text.VersionComparatorUtil
import com.sun.tools.attach.VirtualMachine
import java.io.File
import java.io.IOException
import java.lang.management.ManagementFactory
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.measureTimeMillis

//TODO: report to FUS if we use AppCDS in the product

object CDSManager {
  private val LOG = Logger.getInstance(javaClass)

  val isValidEnv: Boolean
    get() {
      // The AppCDS (JEP 310) is only added in JDK10,
      // The closest JRE we ship/support is 11
      if (!SystemInfo.isJavaVersionAtLeast(11)) return false

      //AppCDS does not support Windows and macOS
      if (!SystemInfo.isLinux) {
        //Specific patches are included into JetBrains runtime
        //to support Windows and macOS
        if (!SystemInfo.isJetBrainsJvm) return false
        if (VersionComparatorUtil.compare(SystemInfo.JAVA_RUNTIME_VERSION, "11.0.4+10-b520.2") < 0) return false
      }

      return true
    }

  val isRunningWithCDS: Boolean
    get() {
      return isValidEnv && currentCDSArchive?.isFile == true
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

  fun cleanupStaleCDSFiles(indicator: ProgressIndicator) = rejectIfRunning(Unit) {
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

  fun installCDS(indicator: ProgressIndicator): CDSPaths? = rejectIfRunning(null) {
    if (VMOptions.getWriteFile() == null) {
      LOG.warn("VMOptions.getWriteFile() == null. Are you running from an IDE run configuration?")
      return null
    }

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

    // we need roughly 400mb of disk space, let's avoid disk space pressure
    if (paths.baseDir.freeSpace < 3L * 1024 * FileUtil.MEGABYTE) {
      LOG.warn("Not enough disk space to enable CDS archive")
      return null
    }

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
          vm.loadAgent(agentPath.path, "${paths.classesListFile}")
        }
        finally {
          vm.detach()
        }
      }
      catch (t: Throwable) {
        LOG.warn("Failed to attach CDS Java Agent to the running IDE instance. ${t.message}\n" +
                 "Please check you have -Djdk.attach.allowAttachSelf=true in the VM options of the IDE", t)
        return null
      }
    }

    LOG.info("CDS classes file is generated in ${StringUtil.formatDuration(durationList)}")

    indicator.text2 = "Generating classes archive..."
    indicator.checkCanceled()

    val logLevel = if (LOG.isDebugEnabled) "=debug" else ""
    val args = listOf(
      "-Djava.class.path=${ManagementFactory.getRuntimeMXBean().classPath}",
      "-Xlog:cds$logLevel",
      "-Xlog:class+path$logLevel",

      "-Xshare:dump",
      "-XX:+UnlockDiagnosticVMOptions",
      "-XX:SharedClassListFile=${paths.classesListFile}",
      "-XX:SharedArchiveFile=${paths.classesArchiveFile}"
    )

    JdkUtil.writeArgumentsToParameterFile(paths.classesPathFile, args)

    val durationLink = measureTimeMillis {
      val ext = if (SystemInfo.isWindows) ".exe" else ""
      val javaExe = File(System.getProperty("java.home")!!) / "bin" / "java$ext"
      val javaArgs = listOf(
        javaExe.path,
        "@${paths.classesPathFile}"
      )

      val cwd = File(".").canonicalFile
      LOG.info("Running CDS generation process: $javaArgs in $cwd with classpath: ${args}")

      indicator.checkCanceled()

      //recreate logs file
      FileUtil.delete(paths.dumpOutputFile)

      val process = ProcessBuilder()
        .directory(cwd)
        .command(javaArgs)
        .redirectErrorStream(true)
        .redirectInput(ProcessBuilder.Redirect.PIPE)
        .redirectOutput(paths.dumpOutputFile)
        .start()

      try {
        process.outputStream.close()
      } catch (t: IOException) {
        //NOP
      }

      try {
        if (!process.waitFor(10, TimeUnit.MINUTES)) {
          LOG.warn("Failed to generate CDS archive, the process took too long and will be killed. See ${paths.dumpOutputFile} for details")
          process.destroyForcibly()
          return null
        }
      } catch (t: InterruptedException) {
        LOG.warn("Failed to generate CDS archive, the process is interrupted")
        process.destroyForcibly()
        return null
      }

      val exitValue = process.exitValue()
      if (exitValue != 0) {
        LOG.warn("Failed to generate CDS archive, the process existed with code $exitValue. See ${paths.dumpOutputFile} for details")
        return null
      }
    }

    VMOptions.writeEnableCDSArchiveOption(paths.classesArchiveFile.absolutePath)

    LOG.warn("Enabled CDS archive from ${paths.classesArchiveFile}, " +
             "size = ${StringUtil.formatFileSize(paths.classesArchiveFile.length())}, " +
             "it took ${StringUtil.formatDuration(durationLink)} (VMOptions were updated)")

    return paths
  }

  fun removeCDS() = rejectIfRunning(Unit) {
    if (!isRunningWithCDS) return
    VMOptions.writeDisableCDSArchiveOption()
    LOG.warn("Disabled CDS")
  }

  private operator fun File.div(s: String) = File(this, s)

  private val ourIsRunning = AtomicBoolean(false)

  private inline fun <T> rejectIfRunning(rejected: T, action: () -> T): T {
    if (!ourIsRunning.compareAndSet(false, true)) return rejected
    try {
      return action()
    } finally {
      ourIsRunning.set(false)
    }
  }
}
