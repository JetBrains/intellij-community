// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal

import com.google.common.primitives.Longs.max
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.indexing.hash.building.IndexChunk
import com.intellij.util.indexing.hash.building.IndexesExporter
import com.intellij.util.io.DigestUtil
import com.intellij.util.io.DigestUtil.updateContentHash
import com.intellij.util.io.isFile
import com.intellij.util.io.sizeOrNull
import com.intellij.util.lang.JavaVersion
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlin.system.exitProcess

class DumpJdkIndexStarter : ApplicationStarter {
  override fun getCommandName() = "dump-jdk-index"

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
    val outputKey = "output-dir"

    println("  [idea] ${commandName} [/$jdkHomeKey=<home to JDK>] /$outputKey=<target output path> /$tempKey=<temp folder>")

    val jdkHome = args.argFile(jdkHomeKey, System.getProperty("java.home"))
    val tempDir = args.argFile(tempKey).recreateDir()
    val outputDir = args.argFile(outputKey).recreateDir()
    val projectDir = File(tempDir, "project").recreateDir()
    val unpackedIndexDir = File(tempDir, "unpacked-index").recreateDir()
    val zipsDir = File(tempDir, "zips").recreateDir()
    val indexZip = File(zipsDir, "index.zip")
    val indexZipXZ = File(zipsDir, "index.zip.xz")

    LOG.info("Resolved jdkHome = $jdkHome")
    LOG.info("Resolved outputDir = $outputDir")
    LOG.info("Resolved tempDir = $tempDir")

    runWriteAction {
      val jdkTable = ProjectJdkTable.getInstance()
      jdkTable.allJdks.forEach {
        LOG.info("Detected SDK $it - removing!")
        jdkTable.removeJdk(it)
      }
    }

    val javaSdkType = JavaSdk.getInstance() as JavaSdkImpl
    val javaSdk = runAndCatchNotNull("create SDK for $jdkHome") {
      SdkConfigurationUtil.createAndAddSDK(jdkHome.absolutePath, javaSdkType)
    }

    val jdkVersion = runAndCatchNotNull("resolve JDK version from path $jdkHome") {
      val version = javaSdkType.getVersionString(javaSdk)?: error("version is <null>")
      val javaVersion = JavaVersion.tryParse(version) ?: error("JavaVersion is null for $version")
      javaVersion.toString()
    }

    LOG.info("Resolved JDK. Version is $jdkVersion, home = ${javaSdk.homePath}")
    val project = run {
      val initProject = runAndCatchNotNull("create project") {
        ProjectManager.getInstance().createProject("jdk-$jdkVersion", projectDir.absolutePath)
      }

      //it is good to open project to make sure Project#isOpen and other similar tests pass
      if (!ProjectManagerEx.getInstanceEx().openProject(initProject)) {
        error("Failed to open project")
      }
      initProject
    }

    LOG.info("Project is read. isOpen=${project.isOpen}")
    runWriteAction {
      ProjectRootManager.getInstance(project).projectSdk = null
    }

    val indexingStartTime = System.currentTimeMillis()

    LOG.info("Collecting SDK roots...")
    val osName = IndexesExporter.getOsNameForIndexVersions()

    //it is up to SdkType to decide on the best SDK contents fingerprint/hash
    val hash = runAndCatchNotNull("compute JDK fingerprint") {
      javaSdkType.computeJdkFingerprint(javaSdk)
    }
    //we need OS in the index hash, let's pretend it's not here
    LOG.info("JDK contents has is: $hash")

    val rootProvider = javaSdk.rootProvider
    val classesRoot = rootProvider.getFiles(OrderRootType.CLASSES).toSet()
    val sourcesRoot = rootProvider.getFiles(OrderRootType.SOURCES).toSet()
    val allRoots = (classesRoot + sourcesRoot).toSet()
    LOG.info("Collected ${allRoots.size} SDK roots")
    val indexChunk = IndexChunk(allRoots, "jdk-$jdkVersion-$osName")
    indexChunk.contentsHash = hash

    IndexesExporter.getInstance(project).exportIndexesChunk(
      indexChunk,
      unpackedIndexDir.toPath(),
      indexZip.toPath())

    val indexingTime = max(0L, System.currentTimeMillis() - indexingStartTime)

    xz(indexZip, indexZipXZ)

    LOG.info("Indexes build completed in ${StringUtil.formatDuration(indexingTime)}")
    LOG.info("JDK size          = ${StringUtil.formatFileSize(jdkHome.totalSize())}")
    LOG.info("JDK hash          = ${hash}")
    LOG.info("Index.zip size    = ${StringUtil.formatFileSize(indexZip.totalSize())}")
    LOG.info("Index.zip.xz size = ${StringUtil.formatFileSize(indexZipXZ.totalSize())}")
    LOG.info("Generated index in $indexZip")

    val indexMetadata = runAndCatchNotNull("extract JSON metadata from $indexZip"){
      ZipFile(indexZip).use { zipFile ->
        val entry = zipFile.getEntry("metadata.json") ?: error("metadata.json is not found")
        val data = zipFile.getInputStream(entry) ?: error("metadata.json is not found")
        data.readBytes()
      }
    }

    val outputNamePrefix = "${indexChunk.name}-$hash-"
    FileUtil.copy(indexZipXZ, File(outputDir, outputNamePrefix + "index.zip.xz"))
    FileUtil.writeToFile(File(outputDir, outputNamePrefix + "metadata.json"), indexMetadata)
    FileUtil.writeToFile(File(outputDir, outputNamePrefix + "index.zip.xz.sha256"), indexZipXZ.sha256())

    exitProcess(0)
  }
}

private fun File.sha256(): String {
  val digest = DigestUtil.sha256()
  updateContentHash(digest, this.toPath())
  return StringUtil.toHexString(digest.digest());
}

private fun xz(file: File, output: File) {
  val bufferSize = 1024 * 1024
  FileUtil.createParentDirs(output)

  try {
    output.outputStream().buffered(bufferSize).use { outputStream ->
      XZOutputStream(outputStream, LZMA2Options()).use { output ->
        file.inputStream().copyTo(output, bufferSize = bufferSize)
      }
    }
  } catch (e: Exception) {
    LOG.error("Failed to generate index.zip.xz package from $file to $output. ${e.message}", e)
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

private fun File.totalSize(): Long {
  if (isFile) return length()
  return Files.walk(this.toPath()).mapToLong {
    when {
      it.isFile() -> java.lang.Long.max(it.sizeOrNull(), 0L)
      else -> 0L
    }
  }.sum()
}

