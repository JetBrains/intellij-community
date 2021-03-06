// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.cds

import com.intellij.diagnostic.VMOptions
import com.intellij.execution.CommandLineWrapperUtil
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessUtil
import com.intellij.execution.util.ExecUtil
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.PerformInBackgroundOption
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.TimeoutUtil
import com.intellij.util.system.CpuArch
import com.sun.management.OperatingSystemMXBean
import com.sun.tools.attach.VirtualMachine
import java.io.File
import java.lang.management.ManagementFactory
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis


sealed class CDSTaskResult(val statusName: String) {
  object Success : CDSTaskResult("success")

  abstract class Cancelled(statusName: String) : CDSTaskResult(statusName)
  object InterruptedForRetry : Cancelled("cancelled")
  object TerminatedByUser : Cancelled("terminated-by-user")
  object PluginsChanged : Cancelled("plugins-changed")

  data class Failed(val error: String) : CDSTaskResult("failed")
}

object CDSManager {
  private val LOG = Logger.getInstance(javaClass)

  val isValidEnv: Boolean by lazy {
    // AppCDS requires classes packed into JAR files, not from the out folder
    if (PluginManagerCore.isRunningFromSources()) return@lazy false

    // CDS features are only available on 64-bit JVMs
    if (CpuArch.is32Bit()) return@lazy false

    // AppCDS does not support Windows and macOS, but specific patches are included into JetBrains runtime
    if (!(SystemInfo.isLinux || SystemInfo.isJetBrainsJvm)) return@lazy false

    // we do not like to overload a potentially small computer with our process
    val osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean::class.java)
    if (osBean.totalPhysicalMemorySize < 4L * 1024 * 1024 * 1024) return@lazy false
    if (osBean.availableProcessors < 4) return@lazy false
    if (!VMOptions.canWriteOptions()) return@lazy false
    true
  }

  val currentCDSArchive: File? by lazy {
    val arguments = ManagementFactory.getRuntimeMXBean().inputArguments

    val xShare = arguments.any { it == "-Xshare:auto" || it == "-Xshare:on" }
    if (!xShare) return@lazy null

    val key = "-XX:SharedArchiveFile="
    return@lazy arguments.firstOrNull { it.startsWith(key) }?.removePrefix(key)?.let(::File)
  }

  fun cleanupStaleCDSFiles(isCDSEnabled: Boolean): CDSTaskResult {
    val paths = CDSPaths.current
    val currentCDSPath = currentCDSArchive

    val files = paths.baseDir.listFiles() ?: arrayOf()
    if (files.isEmpty()) return CDSTaskResult.Success

    for (path in files) {
      if (path == currentCDSPath) continue
      if (isCDSEnabled && paths.isOurFile(path)) continue

      FileUtil.delete(path)
    }

    return CDSTaskResult.Success
  }

  fun removeCDS() {
    if (currentCDSArchive?.isFile != true ) return
    VMOptions.writeDisableCDSArchiveOption()
    LOG.warn("Disabled CDS")
  }

  private interface CDSProgressIndicator {
    /// returns non-null value if cancelled
    val cancelledStatus: CDSTaskResult.Cancelled?
    var text2: String?
  }

  fun installCDS(canStillWork: () -> Boolean, onResult: (CDSTaskResult) -> Unit) {
    CDSFUSCollector.logCDSBuildingStarted()
    val startTime = System.nanoTime()

    ProgressManager.getInstance().run(object : Task.Backgroundable(
      null,
      IdeBundle.message("progress.title.cds.optimize.startup"),
      true,
      PerformInBackgroundOption.ALWAYS_BACKGROUND
    ) {
      override fun run(indicator: ProgressIndicator) {
        indicator.isIndeterminate = true

        val progress = object : CDSProgressIndicator {
          override val cancelledStatus: CDSTaskResult.Cancelled?
            get() {
              if (!canStillWork()) return CDSTaskResult.InterruptedForRetry
              if (indicator.isCanceled) return CDSTaskResult.TerminatedByUser
              if (ApplicationManager.getApplication().isDisposed) return CDSTaskResult.InterruptedForRetry
              return null
            }

          override var text2: String?
            get() = indicator.text2
            set(@NlsContexts.ProgressText value) {
              indicator.text2 = value
            }
        }

        val paths = CDSPaths.current
        val result = try {
          installCDSImpl(progress, paths)
        } catch (t: Throwable) {
          LOG.warn("Settings up CDS Archive crashed unexpectedly. ${t.message}", t)
          val message = "Unexpected crash $t"
          paths.markError(message)
          CDSTaskResult.Failed(message)
        }

        val installTime = TimeoutUtil.getDurationMillis(startTime)
        CDSFUSCollector.logCDSBuildingCompleted(installTime, result)
        onResult(result)
      }
    })
  }

  private fun installCDSImpl(indicator: CDSProgressIndicator, paths: CDSPaths): CDSTaskResult {
    if (paths.isSame(currentCDSArchive)) {
      val message = "CDS archive is already generated and being used. Nothing to do"
      LOG.debug(message)
      return CDSTaskResult.Success
    }

    if (paths.classesErrorMarkerFile.isFile) {
      return CDSTaskResult.Failed("CDS archive has already failed, skipping")
    }

    LOG.info("Starting generation of CDS archive to the ${paths.classesArchiveFile} and ${paths.classesArchiveFile} files")
    paths.mkdirs()

    val listResult = generateFileIfNeeded(indicator, paths, paths.classesListFile, ::generateClassList) { t ->
      "Failed to attach CDS Java Agent to the running IDE instance. ${t.message}"
    }
    if (listResult != CDSTaskResult.Success) return listResult

    val archiveResult = generateFileIfNeeded(indicator, paths, paths.classesArchiveFile, ::generateSharedArchive) { t ->
      "Failed to generated CDS archive. ${t.message}"
    }
    if (archiveResult != CDSTaskResult.Success) return archiveResult

    VMOptions.writeEnableCDSArchiveOption(paths.classesArchiveFile.absolutePath)
    LOG.warn("Enabled CDS archive from ${paths.classesArchiveFile}, VMOptions were updated")
    return CDSTaskResult.Success
  }

  private val agentPath: File
    get() {
      val libPath = File(PathManager.getLibPath()) / "cds" / "classesLogAgent.jar"
      if (libPath.isFile) return libPath

      LOG.warn("Failed to find bundled CDS classes agent in $libPath")

      //consider local debug IDE case
      val probe = File(PathManager.getHomePath()) / "out" / "classes" / "artifacts" / "classesLogAgent_jar" / "classesLogAgent.jar"
      if (probe.isFile) return probe
      error("Failed to resolve path to the CDS agent")
    }

  private fun generateClassList(indicator: CDSProgressIndicator, paths: CDSPaths) {
    indicator.text2 = IdeBundle.message("progress.text.collecting.classes")

    val selfAttachKey = "jdk.attach.allowAttachSelf"
    if (!System.getProperties().containsKey(selfAttachKey)) {
      throw RuntimeException("Please make sure you have -D$selfAttachKey=true set in the VM options")
    }

    val duration = measureTimeMillis {
      val vm = VirtualMachine.attach(OSProcessUtil.getApplicationPid())
      try {
        vm.loadAgent(agentPath.path, "${paths.classesListFile}")
      }
      finally {
        vm.detach()
      }
    }

    LOG.info("CDS classes file is generated in ${StringUtil.formatDuration(duration)}")
  }

  private fun generateSharedArchive(indicator: CDSProgressIndicator, paths: CDSPaths) {
    indicator.text2 = IdeBundle.message("progress.text.generate.classes.archive")

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

    CommandLineWrapperUtil.writeArgumentsFile(paths.classesPathFile, args, StandardCharsets.UTF_8)

    val durationLink = measureTimeMillis {
      val ext = if (SystemInfo.isWindows) ".exe" else ""
      val javaExe = File(System.getProperty("java.home")!!) / "bin" / "java$ext"
      val javaArgs = listOf(
        javaExe.path,
        "@${paths.classesPathFile}"
      )

      val cwd = File(".").canonicalFile
      LOG.info("Running CDS generation process: $javaArgs in $cwd with classpath: ${args}")

      //recreate files for sanity
      FileUtil.delete(paths.dumpOutputFile)
      FileUtil.delete(paths.classesArchiveFile)

      val commandLine = object : GeneralCommandLine() {
        override fun buildProcess(builder: ProcessBuilder) : ProcessBuilder {
          return super.buildProcess(builder)
            .redirectErrorStream(true)
            .redirectInput(ProcessBuilder.Redirect.PIPE)
            .redirectOutput(paths.dumpOutputFile)
        }
      }
      commandLine.workDirectory = cwd
      commandLine.exePath = javaExe.absolutePath
      commandLine.addParameter("@${paths.classesPathFile}")

      if (!SystemInfo.isWindows) {
        // the utility does not recover process exit code from the call on Windows
        ExecUtil.setupLowPriorityExecution(commandLine)
      }

      val timeToWaitFor = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(10)
      fun shouldWaitForProcessToComplete() = timeToWaitFor > System.currentTimeMillis()

      val process = commandLine.createProcess()
      try {
        runCatching { process.outputStream.close() }
        while (shouldWaitForProcessToComplete()) {
          if (indicator.cancelledStatus != null) throw InterruptedException()
          if (process.waitFor(200, TimeUnit.MILLISECONDS)) break
        }
      }
      finally {
        if (process.isAlive) {
          process.destroyForcibly()
        }
      }

      if (!shouldWaitForProcessToComplete()) {
        throw RuntimeException("The process took too long and will be killed. See ${paths.dumpOutputFile} for details")
      }

      val exitValue = process.exitValue()
      if (exitValue != 0) {
        val outputLines = runCatching {
          paths.dumpOutputFile.readLines().takeLast(30).joinToString("\n")
        }.getOrElse { "" }

        throw RuntimeException("The process existed with code $exitValue. See ${paths.dumpOutputFile} for details:\n$outputLines")
      }
    }

    LOG.info("Generated CDS archive in ${paths.classesArchiveFile}, " +
             "size = ${StringUtil.formatFileSize(paths.classesArchiveFile.length())}, " +
             "it took ${StringUtil.formatDuration(durationLink)}")
  }

  private operator fun File.div(s: String) = File(this, s)
  private val File.isValidFile get() = isFile && length() > 42

  private inline fun generateFileIfNeeded(indicator: CDSProgressIndicator,
                                          paths: CDSPaths,
                                          theOutputFile: File,
                                          generate: (CDSProgressIndicator, CDSPaths) -> Unit,
                                          errorMessage: (Throwable) -> String): CDSTaskResult {
    try {
      if (theOutputFile.isValidFile) return CDSTaskResult.Success
      indicator.cancelledStatus?.let { return it }
      if (!paths.hasSameEnvironmentToBuildCDSArchive()) return CDSTaskResult.PluginsChanged

      generate(indicator, paths)
      LOG.assertTrue(theOutputFile.isValidFile, "Result file must be generated and be valid")
      return CDSTaskResult.Success
    }
    catch (t: Throwable) {
      FileUtil.delete(theOutputFile)

      indicator.cancelledStatus?.let { return it }
      if (t is InterruptedException) {
        return CDSTaskResult.InterruptedForRetry
      }

      val message = errorMessage(t)
      LOG.warn(message, t)
      paths.markError(message)
      return CDSTaskResult.Failed(message)
    }
  }
}
