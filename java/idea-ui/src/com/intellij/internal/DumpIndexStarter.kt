// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal

import com.intellij.jdkDownloader.JdkInstallRequest
import com.intellij.jdkDownloader.JdkInstaller
import com.intellij.jdkDownloader.JdkListDownloader
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.util.io.createDirectories
import com.intellij.util.io.delete
import com.intellij.util.io.exists
import com.intellij.util.io.isDirectory
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess

class DumpIndexStarter : ApplicationStarter {
  override fun getCommandName() = "dumpIndex"

  private companion object {

    private val LOG = Logger.getInstance("dumpIndex")

    fun getRequiredProperty(name: String): String =
      System.getProperty(name) ?: throw IllegalArgumentException("'$name' is not specified")

    val outputPath: Path by lazy {
      val path = Paths.get(getRequiredProperty("dump.index.output.path"))
      path.createDirectories()
    }

    val removeDownloadedJdks: Boolean by lazy {
      System.getProperty("dump.index.remove.downloaded.jdks", "false")!!.toBoolean()
    }

    val tempPath: Path by lazy {
      val property = System.getProperty("dump.index.temp.path")
      (if (property != null) Paths.get(property) else outputPath.resolve("temp")).createDirectories()
    }

    fun info(message: String) {
      println(message)
    }
  }

  private fun dumpOptions() {
    info("DumpIndex options")
    info("Output path: $outputPath")
  }

  override fun main(args: Array<out String>) {
    dumpOptions()
    try {
      start()
      info("DumpIndex finished")
      exitProcess(0)
    }
    catch (e: Throwable) {
      info("DumpIndex failed")
      e.printStackTrace()
      exitProcess(-1)
    }
  }

  private fun start() {
    val projectPath = tempPath.resolve("project").createDirectories()
    val jdksPath = tempPath.resolve("jdks").createDirectories()

    info("Downloading list of available JDKs")
    val jdkItems = JdkListDownloader.downloadModelForJdkInstaller(EmptyProgressIndicator())
    info("Available JDKs: " + jdkItems.joinToString { it.fullPresentationText })

    val project = ProjectManager.getInstance().createProject("tempProject", projectPath.toAbsolutePath().toString())
    if (project == null) {
      LOG.error("Failed to create temp project")
      return
    }

    val jdkType = JavaSdk.getInstance()
    for (jdkItem in jdkItems) {
      val jdkPath = jdksPath.resolve(jdkItem.installFolderName)

      if (jdkPath.isDirectory()) {
        info("JDK ${jdkItem.fullPresentationText} is already downloaded")
      }
      else {
        info("Downloading ${jdkItem.fullPresentationText} to $jdkPath")
        val jdkInstallRequest = JdkInstallRequest(jdkItem, jdkPath.toFile())
        JdkInstaller.installJdk(jdkInstallRequest, EmptyProgressIndicator())
      }

      info("Creating SDK from ${jdkItem.fullPresentationText}")
      val sdk = SdkConfigurationUtil.createAndAddSDK(jdkPath.toAbsolutePath().toString(), jdkType)
      if (sdk == null) {
        LOG.warn("Failed to create JDK for ${jdkItem.fullPresentationText}")
        continue
      }

      val jdkIndicesPath = outputPath.resolve(jdkItem.installFolderName)
      if (jdkIndicesPath.exists()) {
        info("Delete previous indices in $jdkIndicesPath")
      }

      info("Building indices for ${jdkItem.fullPresentationText} to $jdkIndicesPath")
      try {
        val roots = (sdk.rootProvider.getFiles(OrderRootType.CLASSES) + sdk.rootProvider.getFiles(OrderRootType.SOURCES)).toSet()
        val indexChunk = DumpIndexAction.IndexChunk(roots, jdkItem.installFolderName)
        DumpIndexAction.exportSingleIndexChunk(project, indexChunk, jdkIndicesPath, EmptyProgressIndicator())
      }
      finally {
        SdkConfigurationUtil.removeSdk(sdk)
        if (removeDownloadedJdks) {
          jdkPath.delete()
        }
      }
    }
  }
}