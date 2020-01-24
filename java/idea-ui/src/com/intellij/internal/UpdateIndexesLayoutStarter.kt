// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import kotlin.system.exitProcess

class UpdateIndexesLayoutStarter : IndexesStarterBase("update-index-layout") {
  override fun mainImpl(args: Array<out String>) {
    println("Update indexes layout and generates all necessary index files")
    println("usage:")
    println("  [idea] $commandName /output-dir=<output directory> /base-url=<base url>")
    println()

    val outputDir =  args.argFile("output-dir")
    val baseUrl = args.arg("base-url")

    if (!outputDir.isDirectory) {
      println("Directory $outputDir does not exist or is a file. Nothing to do.")
      exitProcess(2)
    }

    val allHashes = runAndCatchNotNull("failed to list $outputDir") {
      outputDir.listFiles()
    }.filter { it.isDirectory }.sortedBy { it.name }

    LOG.info("Detected ${allHashes.size} folders to index...")

    for (hash in allHashes) {
      LOG.info("Updating ${hash.name}...")
      rebuildIndexForHashDir(hash, baseUrl)
    }
  }

  companion object {
    fun rebuildIndexForHashDir(hashDir: File, baseUrl: String) {
      val hashCode = hashDir.name

      data class IndexFile(
        val indexFile: File,
        val metadataFile: File,
        val sha256File: File
      ) {
        val metadata by lazy { metadataFile.readBytes() }
        val sha256 by lazy { sha256File.readText().trim() }

        val indexFileName get() = indexFile.name
      }

      val indexes: List<IndexFile> = (hashDir.listFiles()?.toList() ?: listOf())
        .groupBy{ it.nameWithoutExtension }
        .mapNotNull { (_, group) ->
          val indexFile = group.firstOrNull { it.path.endsWith(".ijx") }  ?: return@mapNotNull null
          val metadataFile = group.firstOrNull { it.path.endsWith(".json") }  ?: return@mapNotNull null
          val shaFile = group.firstOrNull { it.path.endsWith(".sha256") }  ?: return@mapNotNull null
          IndexFile(indexFile, metadataFile, shaFile)
        }.sortedBy { it.indexFileName }

      val om = ObjectMapper()
      val root = om.createObjectNode()

      root.put("list_version", "1")
      val entries = root.putArray("entries")

      for (idx in indexes) {
        val entry = entries.addObject()

        entry.put("url", "$baseUrl/$hashCode/${idx.indexFileName}")
        entry.put("sha256", idx.sha256)
        entry.set<ObjectNode>("metadata", om.readTree(idx.metadata))
      }

      val listData = om.writerWithDefaultPrettyPrinter().writeValueAsBytes(root)
      FileUtil.writeToFile(File(hashDir, "index.json"), listData)
    }
  }
}
