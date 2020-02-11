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
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.indexing.IndexInfrastructureVersion
import com.intellij.util.indexing.hash.building.IndexChunk
import com.intellij.util.indexing.hash.building.IndexesExporter
import com.intellij.util.lang.JavaVersion
import com.intellij.util.text.nullize
import kotlin.system.exitProcess

class DumpJdkIndexStarter : IndexesStarterBase("dump-jdk-index") {
  override fun mainImpl(args: Array<out String>) {
    println("Dump JDK indexes command:")
    val nameHintKey = "name-infix"
    val jdkHomeKey = "jdk-home"
    val alias = "alias"

    println("")
    println("  [idea] ${commandName} ... (see keys below)")
    println("       --$jdkHomeKey=<home to JDK>    --- path to JDK to index")
    println("       [--$nameHintKey=<infix>]       --- name suffix to for the generated files")
    println("                                         a hint to the later indexes management")
    println("       [--$alias=<alias>]             --- alias for a given entry, allows multiple usages")
    println()
    tempKey.usage()
    outputKey.usage()
    println("")

    val nameHint = args.arg(nameHintKey, "").nullize(true)
    val jdkHome = args.argFile(jdkHomeKey, System.getProperty("java.home"))
    val tempDir = args.argFile(tempKey).recreateDir()
    val outputDir = args.argFile(outputKey).apply { mkdirs() }
    val projectDir = (tempDir / "project").recreateDir()
    val zipsDir = (tempDir / "zips").recreateDir()
    val indexZip = zipsDir / "index.zip"
    val aliases = args.args(alias)

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
    val os = IndexInfrastructureVersion.Os.getOs()

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
    val indexName = "jdk-$jdkVersion-${os.osName}${nameHint?.let { "-$it" } ?: ""}"
    val indexKind = "jdk"
    val indexChunk = IndexChunk(allRoots, indexName)

    LOG.info("Indexing...")
    val infraVersion = IndexesExporter.getInstance(project).exportIndexesChunk(indexChunk, indexZip.toPath())

    val indexingTime = max(0L, System.currentTimeMillis() - indexingStartTime)
    LOG.info("Indexes build completed in ${StringUtil.formatDuration(indexingTime)}")
    LOG.info("JDK size          = ${StringUtil.formatFileSize(jdkHome.totalSize())}")
    LOG.info("JDK hash          = ${hash}")

    packIndexes(indexKind, indexName, hash, indexZip, infraVersion, outputDir, aliases = aliases)
    exitProcess(0)
  }
}

