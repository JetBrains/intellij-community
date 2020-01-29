// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal

import com.google.common.primitives.Longs.max
import com.intellij.openapi.application.runWriteAction
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
import com.intellij.util.indexing.IndexInfrastructureVersion
import com.intellij.util.indexing.hash.building.IndexChunk
import com.intellij.util.indexing.hash.building.IndexesExporter
import com.intellij.util.lang.JavaVersion
import com.intellij.util.text.nullize
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream
import java.io.File
import java.util.zip.ZipFile
import kotlin.system.exitProcess

class DumpJdkIndexStarter : IndexesStarterBase("dump-jdk-index") {
  override fun mainImpl(args: Array<out String>) {
    println("Dump JDK indexes command:")
    val nameHintKey = "name-infix"
    val jdkHomeKey = "jdk-home"
    val tempKey = "temp"
    val outputKey = "output"
    val baseUrlKey = "base-url"

    println("")
    println("  [idea] ${commandName} ... (see keys below)")
    println("       [--$jdkHomeKey=<home to JDK>]  --- path to JDK to index")
    println("       [--$nameHintKey=<infix>]       --- name suffix to for the generated files")
    println("                                         a hint to the later indexes management")
    println("       --$tempKey=<temp folder>       --- path where temp files can be created,")
    println("                                         (!) it will be cleared by the tool")
    println("       --$outputKey=<output path>     --- location of the indexes CDN image,")
    println("                                         it will be updated with new data")
    println("       [--$baseUrlKey=<URL>]          --- CDN base URL for index.json files")
    println("")
    println("")
    println("")

    val nameHint = args.arg(nameHintKey, "").nullize(true)
    val baseUrl = args.arg(baseUrlKey, "").nullize(true)?.trim()?.trimEnd('/')
    val jdkHome = args.argFile(jdkHomeKey, System.getProperty("java.home"))
    val tempDir = args.argFile(tempKey).recreateDir()
    val outputDir = args.argFile(outputKey).apply { mkdirs() }
    val projectDir = File(tempDir, "project").recreateDir()
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
    val os = IndexInfrastructureVersion.getOs()

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

    LOG.info("Collected ${allRoots.size} SDK roots...")
    val indexChunk = IndexChunk(allRoots, "jdk-$jdkVersion-${os.osName}${nameHint?.let {"-$it"} ?: ""}")
    indexChunk.contentsHash = hash
    indexChunk.kind = "jdk"

    LOG.info("Indexing...")
    val indexerInfra = IndexesExporter.getInstance(project).exportIndexesChunk(indexChunk, indexZip.toPath())

    LOG.info("Packing the indexes to XZ...")
    xz(indexZip, indexZipXZ)

    val indexingTime = max(0L, System.currentTimeMillis() - indexingStartTime)
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

    //we generate production layout here:
    // <kind>
    //   |
    //   | <hash>
    //       |
    //       | index.json  // (contains the listing of all entries for a given hash)
    //       |
    //       | <entry>.ijx    // an entry that is listed
    //       | <entry>.json   // that entry metadata (same as in the metadata.json)
    //       | <entry>.sha256 // hashcode of the entry
    //

    val indexDir = File(File(outputDir, indexChunk.kind!!), indexChunk.contentsHash!!).apply { mkdirs() }
    fun indexFile(nameSuffix: String) = File(indexDir, indexChunk.name + "-${indexerInfra.weakVersionHash}" + nameSuffix)

    FileUtil.copy(indexZipXZ, indexFile(".ijx"))
    FileUtil.writeToFile(indexFile(".json"), indexMetadata)
    FileUtil.writeToFile(indexFile(".sha256"), indexZipXZ.sha256())
    if (baseUrl != null) {
      UpdateIndexesLayoutStarter.rebuildIndexForHashDir(indexDir, baseUrl)
    }

    exitProcess(0)
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
}

