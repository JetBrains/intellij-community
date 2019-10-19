// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.cds

import com.intellij.diagnostic.VMOptions
import com.intellij.execution.process.OSProcessUtil
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.*
import com.intellij.openapi.projectRoots.JdkUtil
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.text.VersionComparatorUtil
import com.sun.management.OperatingSystemMXBean
import com.sun.tools.attach.VirtualMachine
import java.io.File
import java.io.IOException
import java.lang.management.ManagementFactory
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis


private interface CDSProgressIndicator {
  val isCancelled: Boolean
  var text2: String?
}

sealed class CDSTaskResult {
  object Success : CDSTaskResult()
  object Cancelled : CDSTaskResult()
  data class Failed(val error: String) : CDSTaskResult()
}

object CDSManager {
  private val LOG = Logger.getInstance(javaClass)

  val isValidEnv: Boolean by lazy {
    // AppCDS requires classes packed into JAR files, not from the out folder
    if (PluginManagerCore.isRunningFromSources()) return@lazy false

    // CDS features are only available on 64 bit JVMs
    if (!SystemInfo.is64Bit) return@lazy false

    // The AppCDS (JEP 310) is only added in JDK10,
    // The closest JRE we ship/support is 11
    if (!SystemInfo.isJavaVersionAtLeast(11)) return@lazy false

    //AppCDS does not support Windows and macOS
    if (!SystemInfo.isLinux) {
      //Specific patches are included into JetBrains runtime
      //to support Windows and macOS
      if (!SystemInfo.isJetBrainsJvm) return@lazy false
      if (VersionComparatorUtil.compare(SystemInfo.JAVA_RUNTIME_VERSION, "11.0.4+10-b520.2") < 0) return@lazy false
    }

    // we do not like to overload a potentially small computer with our process
    val osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean::class.java)
    if (osBean.totalPhysicalMemorySize < 3L * 1024L * 1024 * 1024) return@lazy false
    if (osBean.availableProcessors < 2) return@lazy false

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

  fun installCDS(canStillWork: () -> Boolean, onResult: (CDSTaskResult) -> Unit) {
    CDSFUSCollector.logCDSBuildingStarted()
    val startTime = System.currentTimeMillis()

    ProgressManager.getInstance().run(object : Task.Backgroundable(
      null,
      "Optimizing startup performance",
      true,
      PerformInBackgroundOption.ALWAYS_BACKGROUND
    ) {
      override fun run(indicator: ProgressIndicator) {
        indicator.isIndeterminate = true

        val progress = object : CDSProgressIndicator {
          override val isCancelled: Boolean
            get() = !canStillWork() || indicator.isCanceled

          override var text2: String?
            get() = indicator.text2
            set(value) {
              indicator.text2 = value
            }
        }

        onResult(installCDSSafe(progress, indicator, startTime))
      }
    })
  }

  private fun installCDSSafe(progress: CDSProgressIndicator,
                             indicator: ProgressIndicator,
                             startTime: Long): CDSTaskResult {
    val paths = CDSPaths.current
    try {
      val result = installCDSImpl(progress, paths)
      val installTime = System.currentTimeMillis() - startTime
      return when (result) {
        is CDSTaskResult.Success -> {
          CDSFUSCollector.logCDSBuildingCompleted(installTime)
          result
        }
        is CDSTaskResult.Failed -> {
          CDSFUSCollector.logCDSBuildingFailed()
          result
        }
        is CDSTaskResult.Cancelled -> {
          // detect an User attempt to stop AppCDS generation
          // turn in to unrecoverable failure to avoid bothering for that version
          if (indicator.isCanceled) {
            CDSFUSCollector.logCDSBuildingStoppedByUser()
            paths.markError("User Interrupted")
            CDSTaskResult.Failed("User Interrupted")
          } else {
            CDSFUSCollector.logCDSBuildingInterrupted()
            result
          }
        }
      }
    }
    catch (t: Throwable) {
      LOG.warn("Settings up CDS Archive crashed unexpectedly. ${t.message}", t)
      val message = "Unexpected crash $t"
      paths.markError(message)
      CDSFUSCollector.logCDSBuildingFailed()
      return CDSTaskResult.Failed(message)
    }
  }

  private fun installCDSImpl(indicator: CDSProgressIndicator, paths: CDSPaths): CDSTaskResult {
    if (paths.isSame(currentCDSArchive)) {
      val message = "CDS archive is already generated and being used. Nothing to do"
      LOG.debug(message)
      return CDSTaskResult.Success
    }

    if (paths.classesErrorMarkerFile.isFile) {
      return CDSTaskResult.Failed("CDS archive has already failed to set up, skipping")
    }

    LOG.info("Starting generation of CDS archive to the ${paths.classesArchiveFile} and ${paths.classesArchiveFile} files")
    paths.mkdirs()

    val listResult = generateFileIfNeeded(indicator, paths, paths.classesListFile, ::generateClassesList) { t ->
      "Failed to attach CDS Java Agent to the running IDE instance. ${t.message}"
    }
    if (listResult != CDSTaskResult.Success) return listResult

    val archiveResult = generateFileIfNeeded(indicator, paths, paths.classesArchiveFile, ::generateClassesArchive) { t ->
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

  private fun generateClassesList(indicator: CDSProgressIndicator, paths: CDSPaths) {
    indicator.text2 = "Collecting classes list..."

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

  private fun generateClassesArchive(indicator: CDSProgressIndicator,
                                     paths: CDSPaths) {
    indicator.text2 = "Generating classes archive..."

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
        try {
          process.outputStream.close()
        }
        catch (t: IOException) {
          //NOP
        }

        val timeToWaitFor = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(10)
        while (timeToWaitFor > System.currentTimeMillis()) {
          if (indicator.isCancelled) throw InterruptedException()
          if (process.waitFor(200, TimeUnit.MILLISECONDS)) break
        }

        if (process.isAlive) {
          process.destroyForcibly()
          throw RuntimeException("The process took too long and will be killed. See ${paths.dumpOutputFile} for details")
        }
      }
      finally {
        if (process.isAlive) {
          process.destroyForcibly()
        }
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
      if (indicator.isCancelled) return CDSTaskResult.Cancelled
      generate(indicator, paths)
      LOG.assertTrue(theOutputFile.isValidFile, "Result file must be generated and be valid")
      return CDSTaskResult.Success
    }
    catch (t: Throwable) {
      FileUtil.delete(theOutputFile)

      if (t is InterruptedException || t is ProcessCanceledException || indicator.isCancelled) {
        return CDSTaskResult.Cancelled
      }

      val message = errorMessage(t)
      LOG.warn(message, t)
      paths.markError(message)
      return CDSTaskResult.Failed(message)
    }
  }
}
