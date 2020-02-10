// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal

import com.google.common.primitives.Longs
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.indexing.hash.building.ProjectIndexesExporter
import java.io.File
import kotlin.system.exitProcess

class DumpProjectIndexesStarter : IndexesStarterBase("dump-project-index") {

  override fun mainImpl(args: Array<out String>) {
    //disable indexing
    //System.setProperty("idea.skip.indices.initialization", "true")
    System.setProperty("idea.force.dumb.queue.tasks", "true")

    val revisionHash = CommandLineKey("commit") {
      println("       --$name=<commit hash>       --- project revision")
    }
    val projectHome = CommandLineKey("project") {
      println("       --$name=<home to project>    --- path to IntelliJ project")
    }

    println("")
    println("  [idea] ${commandName} ... (see keys below)")
    projectHome.usage()
    revisionHash.usage()
    tempKey.usage()
    outputKey.usage()
    println("")
    println("")
    println("")

    val tempDir = args.argFile(tempKey).recreateDir()
    val outputDir = args.argFile(outputKey).apply { mkdirs() }
    val projectDir = args.argFile(projectHome)
    val hash = args.arg(revisionHash)

    LOG.info("Opening project from $projectDir")
    val project = runAndCatchNotNull("Opening Project") {
      ProjectManager.getInstance().loadAndOpenProject(projectDir)
    }

    val chunkName = FileUtil.sanitizeFileName(project.name, true)
    val indexZip = File(tempDir, "project-$hash-$chunkName.ijx")

    LOG.info("Generating indexes...")
    val indexingStartTime = System.currentTimeMillis()
    val infraVersion = ProjectIndexesExporter
      .getInstance(project)
      .exportIndices(indexZip.toPath(), EmptyProgressIndicator())

    val indexingTime = Longs.max(0L, System.currentTimeMillis() - indexingStartTime)
    LOG.info("Indexes build completed in ${StringUtil.formatDuration(indexingTime)}")
    LOG.info("size          = ${StringUtil.formatFileSize(indexZip.totalSize())}")
    LOG.info("hash          = ${hash}")

    packIndexes("project", chunkName, hash, indexZip, infraVersion, outputDir)
    exitProcess(0)
  }
}