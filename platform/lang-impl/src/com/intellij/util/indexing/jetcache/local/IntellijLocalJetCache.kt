// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.jetcache.local

import com.intellij.openapi.util.io.ByteArraySequence
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.concurrency.SequentialTaskExecutor
import com.intellij.util.indexing.ContentHashesSupport
import com.intellij.util.indexing.jetcache.JetCacheService
import org.jetbrains.jetcache.JcHash
import org.jetbrains.jetcache.JetCache
import java.lang.UnsupportedOperationException
import java.util.concurrent.CompletableFuture

class IntellijLocalJetCache : JetCache {
  val executor = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("jetcache local executor")

  override fun get(keys: Array<JcHash>, onReceived: (JcHash, ByteArray) -> Unit) {
    executor.submit {
      try {
        for (key in keys) {
          val hashAndId = ContentHashesSupport.splitHashAndId(key)
          if (hashAndId != null) {
            val storage = JetCacheService.instance.getStorage(hashAndId.second)
            storage?.get(hashAndId.first)?.toBytes()?.let { onReceived(key, it) }
          }
        }
        //onFinished(true)
      }
      catch (e: Exception) {
        //onFinished(false)
      }
    }
  }

  override fun getMultiple(key: JcHash): CompletableFuture<Array<ByteArray>> {
    val completableFuture = CompletableFuture<Array<ByteArray>>()
    executor.submit {
      try {
        completableFuture.complete(JetCacheService.instance.groupIdMap!!.getMultiple(key))
      }
      catch (e: Exception) {
        completableFuture.completeExceptionally(e)
      }
    }
    return completableFuture
  }

  override fun merge(key: JcHash, values: Array<ByteArray>) {
    JetCacheService.instance.groupIdMap!!.append(key, values)
  }

  override fun put(key: JcHash, value: ByteArray) {
    executor.submit {
      val hashAndId = ContentHashesSupport.splitHashAndId(key)
      if (hashAndId != null) {
        val storage = JetCacheService.instance.getStorage(hashAndId.second)
        storage?.put(hashAndId.first, ByteArraySequence(value))
      }
    }
  }
}