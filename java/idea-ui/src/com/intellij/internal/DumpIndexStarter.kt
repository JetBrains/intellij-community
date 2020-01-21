// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal

import com.intellij.jdkDownloader.JdkInstallRequest
import com.intellij.jdkDownloader.JdkInstaller
import com.intellij.jdkDownloader.JdkListDownloader
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.io.*
import com.intellij.util.lang.JavaVersion
import java.io.File
import java.lang.Long.max
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis

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
        buildIndexes(project,
                     sdk,
                     jdkItem.fullPresentationText,
                     jdkIndicesPath,
                     jdkIndicesPath.resolve(jdkIndicesPath.fileName.toString() + ".zip"),
                     EmptyProgressIndicator()
        )
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

private fun buildIndexes(project: Project,
                         sdk: Sdk,
                         indexChunkName: String,
                         temp: Path,
                         outZipFile: Path,
                         indicator: ProgressIndicator) {
  LOG.info("Collecting SDK roots...")

  val rootProvider = sdk.rootProvider
  val roots = (rootProvider.getFiles(OrderRootType.CLASSES) + rootProvider.getFiles(OrderRootType.SOURCES)).toSet()
  LOG.info("Collected ${roots.size} SDK roots")
  val indexChunk = DumpIndexAction.IndexChunk(roots, indexChunkName)
  DumpIndexAction.exportSingleIndexChunk(project, indexChunk, temp, outZipFile, indicator)
}

class DumpJdkIndexStarter : ApplicationStarter {
  override fun getCommandName() = "dump-jdk-index"

  private fun Array<out String>.arg(arg: String, default: String? = null): String {
    val key = "/$arg="
    val values = filter { it.startsWith(key) }.map { it.removePrefix(key) }

    if (values.isEmpty() && default != null) {
      return default
    }
    require(values.size == 1) { "Commandline argument $key is missing or defined multiple times" }
    return values.first()
  }

  private fun Array<out String>.argFile(arg: String, default: String? = null) = File(arg(arg, default)).canonicalFile

  private fun <Y: Any> runAndCatchNotNull(errorMessage: String, action: () -> Y?) : Y {
    try {
      return action() ?: error("<null> was returned!")
    }
    catch (t: Throwable) {
      throw Error("Failed to $errorMessage. ${t.message}", t)
    }
  }

  private fun File.recreateDir() = apply {
    FileUtil.delete(this)
    FileUtil.createDirectory(this)
  }

  override fun main(args: Array<out String>) {
    try {
      mainImpl(args)
    } catch (t: Throwable) {
      LOG.error("JDK Indexing failed unexpectedly. ${t.message}", t)
      exitProcess(1)
    }
  }

  private fun mainImpl(args: Array<out String>) {
    println("Dump JDK indexes command:")
    val jdkHomeKey = "jdk-home"
    val tempKey = "temp"
    val outputKey = "index-zip"

    println("  [idea] ${commandName} [/$jdkHomeKey=<home to JDK>] /$outputKey=<target output path> /$tempKey=<temp folder>")

    val jdkHome = args.argFile(jdkHomeKey, System.getProperty("java.home"))
    val tempDir = args.argFile(tempKey).recreateDir()
    val indexZip = args.argFile(outputKey).apply {
      FileUtil.delete(this)
      FileUtil.createParentDirs(this)
    }
    val projectDir = File(tempDir, "project").recreateDir()
    val unpackedIndexDir = File(tempDir, "unpacked-index").recreateDir()

    LOG.info("Resolved jdkHome = $jdkHome")
    LOG.info("Resolved indexZip = $indexZip")
    LOG.info("Resolved tempDir = $tempDir")

    runWriteAction {
      val jdkTable = ProjectJdkTable.getInstance()
      jdkTable.allJdks.forEach {
        LOG.info("Detected SDK $it - removing!")
        jdkTable.removeJdk(it)
      }
    }

    val javaSdkType = JavaSdk.getInstance()
    val javaSdk = runAndCatchNotNull("create SDK for $jdkHome") {
      SdkConfigurationUtil.createAndAddSDK(jdkHome.absolutePath, javaSdkType)
    }

    val jdkVersion = runAndCatchNotNull("resolve JDK version from path $jdkHome") {
      val version = javaSdkType.getVersionString(javaSdk)?: error("version is <null>")
      val javaVersion = JavaVersion.tryParse(version) ?: error("JavaVersion is null for $version")
      javaVersion.toString()
    }

    LOG.info("Resolved JDK. Version is $jdkVersion, home = ${javaSdk.homePath}")
    val project = runAndCatchNotNull("create project") {
      ProjectManager.getInstance().createProject("jdk-$jdkVersion", projectDir.absolutePath)
    }

    LOG.info("Project is read. isOpen=${project.isOpen}")
    runWriteAction {
      ProjectRootManager.getInstance(project).projectSdk = null
    }

    val time = measureTimeMillis {
      buildIndexes(project,
                   javaSdk,
                   "jdk-$jdkVersion",
                   unpackedIndexDir.toPath(),
                   indexZip.toPath(),
                   EmptyProgressIndicator())
    }

    val jdkSize = jdkHome.totalSize()
    val indexSize = indexZip.totalSize()

    LOG.info("Indexes build completed in ${StringUtil.formatDuration(time)}")
    LOG.info("JDK size   = ${StringUtil.formatFileSize(jdkSize)}")
    LOG.info("Index size = ${StringUtil.formatFileSize(indexSize)}")
    LOG.info("Generated index in $indexZip")
    exitProcess(0)
  }

  private fun File.totalSize(): Long {
    if (isFile) return length()
    return Files.walk(this.toPath()).mapToLong {
      when {
        it.isFile() -> max(it.sizeOrNull(), 0L)
        else -> 0L
      }
    }.sum()
  }
}

private object LOG {
  fun info(message: String) {
    println(message)
  }

  fun error(message: String, cause: Throwable? = null) {
    Logger.getInstance(DumpJdkIndexStarter::class.java).error(message, cause)
    println("ERROR - $message")
    cause?.printStackTrace()
  }
}

