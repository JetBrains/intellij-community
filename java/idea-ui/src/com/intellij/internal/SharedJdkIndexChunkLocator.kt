// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal

import com.intellij.concurrency.JobLauncher
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl
import com.intellij.openapi.roots.JdkOrderEntry
import com.intellij.openapi.roots.OrderEntry
import com.intellij.util.Consumer
import com.intellij.util.indexing.provided.SharedIndexChunkLocator
import java.nio.file.Path

class SharedJdkIndexChunkLocator: SharedIndexChunkLocator {
  override fun locateIndex(project: Project,
                           entries: MutableCollection<out OrderEntry>,
                           descriptorProcessor: Consumer<in SharedIndexChunkLocator.ChunkDescriptor>,
                           indicator: ProgressIndicator) {
    //TODO: it should cache the known objects to return them offline first
    //TODO: what if I have a fresh index update for an already downloaded chunk?

    val jdkToEntries = entries.filterIsInstance<JdkOrderEntry>()
      .mapNotNull { it.jdk?.to(it) }
      .filter { it.first.sdkType is JavaSdkImpl }
      .groupBy({ it.first }, {it.second})
      .toMap()

    if (jdkToEntries.isEmpty()) return
    val type = JavaSdk.getInstance() as JavaSdkImpl

    val sharedIndexType = "jdk"
    JobLauncher.getInstance().invokeConcurrentlyUnderProgress(jdkToEntries.entries.toList(), indicator) { (sdk, entries) ->
      val sdkHash = type.computeJdkFingerprint(sdk) ?: return@invokeConcurrentlyUnderProgress true
      val info = SharedIndexesLoader.getInstance().lookupSharedIndex(
        SharedIndexRequest(kind = sharedIndexType, hash = sdkHash), indicator)
      info ?: return@invokeConcurrentlyUnderProgress true

      descriptorProcessor.consume(object: SharedIndexChunkLocator.ChunkDescriptor {
        override fun getChunkUniqueId() = "jdk-$sdkHash-${info.version.weakVersionHash}"
        override fun getOrderEntries() = entries
        override fun downloadChunk(targetFile: Path, indicator: ProgressIndicator) {
          SharedIndexesLoader.getInstance().downloadSharedIndex(info, indicator, targetFile.toFile())
        }
      })
      true
    }
  }
}