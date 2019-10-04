// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.cds

import com.intellij.execution.process.OSProcessUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.sun.tools.attach.VirtualMachine
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

object CDSArchiveManager {
  private val LOG = Logger.getInstance(javaClass)

  fun injectCDSAgent() {
    // The AppCDS (JEP 350) is only added in JDK10,
    // The closest JRE we ship/support is 11
    if (!SystemInfo.isJavaVersionAtLeast(11)) return

    //We use AppCDS feature that require
    // patches to work in Windows and macOS
    if (!SystemInfo.isJetBrainsJvm) return

    val info = ApplicationInfo.getInstance()
    val prefix = "classes-${info.build.asString()}-${System.currentTimeMillis()}"

    val cwd = File(".").canonicalFile
    val agentPath = System.getProperty("intellij.cds.agentlib", "E:\\Work\\intellij\\out\\classes\\artifacts\\classesLogAgent_jar\\classesLogAgent.jar")

    val baseDir = File(PathManager.getSystemPath(), "cds")
    baseDir.mkdirs()

    //TODO: cleanup stale files
    val classesListFile = File(baseDir, "$prefix.txt")
    val classesPathFile = File(baseDir, "$prefix.classpath")
    val classesArchiveFile = File(baseDir, "$prefix.jsa")

    LOG.info("Starting generation of CDS archive to the $classesListFile and $classesPathFile files")

    val durationList = measureTimeMillis {
      try {
        val vm = VirtualMachine.attach(OSProcessUtil.getApplicationPid())
        try {
          vm.loadAgent(agentPath, "$classesListFile,$classesPathFile")
        }
        finally {
          vm.detach()
        }
      }
      catch (t: Throwable) {
        LOG.warn("Failed to attach CDS Java Agent to the running IDE instance. ${t.message}", t)
      }
    }

    LOG.info("CDS classes file is generated in ${StringUtil.formatDuration(durationList)}")
    LOG.info("Running JVM to dump classes...")

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

      LOG.info("Running CDS generation process: $args in $cwd")

      val process = ProcessBuilder().command(args).inheritIO().directory(cwd).start()
      if (!process.waitFor(10, TimeUnit.MINUTES)) {
        LOG.warn("Failed to generate CDS archive, the process took too long and will be killed")
        process.destroyForcibly()
        return
      }
    }

    LOG.info("Generated CDS archive to $classesArchiveFile, size = ${StringUtil.formatFileSize(classesArchiveFile.length())} in ${StringUtil.formatDuration(durationLink)}")

    //TODO: update VMOptions
  }
}


class InstallCDSArchiveAction : AnAction("Install CDS Archive") {
  override fun actionPerformed(e: AnActionEvent) {
    AppExecutorUtil.getAppExecutorService().execute { CDSArchiveManager.injectCDSAgent() }
  }
}

private operator fun File.div(s: String) = File(this, s)

