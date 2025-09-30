// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.managed.cache.backend

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.platform.managed.cache.CacheId
import com.intellij.platform.managed.cache.RemoteManagedCacheApi
import com.intellij.platform.managed.cache.RemoteManagedCacheBuildParams
import com.intellij.platform.project.findProjectOrNull
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.KeyDescriptor
import com.intellij.util.io.cache.ManagedCache
import com.intellij.util.io.cache.ManagedCacheFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.io.DataInput
import java.io.DataOutput
import java.nio.file.Path

private typealias EntriesFlow = Flow<Pair<ByteArray, ByteArray>>

private fun remoteCacheLocation(): Path {
  return PathManager.getSystemDir().resolve("remote-cache")
}

@Service(Service.Level.PROJECT)
private class RemoteManagedCacheManager(private val project: Project, private val coroutineScope: CoroutineScope) {
  private val storage = ConcurrentCollectionFactory.createConcurrentMap<String, ManagedCache<ByteArray, ByteArray>>()
  fun get(cacheId: CacheId): ManagedCache<ByteArray, ByteArray> {
    return storage[cacheId.name]!!
  }
  suspend fun create(cacheId: CacheId, buildParams: RemoteManagedCacheBuildParams): EntriesFlow {
    val cache = storage[cacheId.name]
                ?: ManagedCacheFactory.getInstance().createCache(
                  project,
                  remoteCacheLocation(),
                  cacheId.name,
                  MyKeyDescriptor(),
                  Externalizer(),
                  buildParams.serdeVersion,
                  coroutineScope
                ).also { storage[cacheId.name] = it }
    return cache.entries()
  }

  private open class Externalizer : DataExternalizer<ByteArray> {
    override fun save(out: DataOutput, value: ByteArray) {
      out.writeInt(value.size)
      out.write(value)
    }

    override fun read(`in`: DataInput): ByteArray {
      val dataSize = `in`.readInt()
      val data = ByteArray(dataSize)
      `in`.readFully(data)
      return data
    }
  }

  private class MyKeyDescriptor : Externalizer(), KeyDescriptor<ByteArray> {
    override fun getHashCode(value: ByteArray): Int = value.contentHashCode()
    override fun isEqual(val1: ByteArray?, val2: ByteArray?): Boolean {
      return val1?.contentEquals(val2) ?: (val2 == null)
    }
  }
}

internal class RemoteManagedCacheApiImpl: RemoteManagedCacheApi {
  private suspend fun CacheId.cache():  ManagedCache<ByteArray, ByteArray>? {
    val project = projectId.findProjectOrNull()?.takeUnless { it.isDisposed } ?: return null
    val cacheManager = project.serviceAsync<RemoteManagedCacheManager>()
    return cacheManager.get(this)
  }
  override suspend fun get(cacheId: CacheId, key: ByteArray): ByteArray? {
    return cacheId.cache()?.get(key)
  }

  override suspend fun put(cacheId: CacheId, key: ByteArray, value: ByteArray?) {
    cacheId.cache()?.let {
      if (value == null) {
        it.remove(key)
      } else {
        it.put(key, value)
      }
    }
  }

  override suspend fun createCacheAndProvideEntries(cacheId: CacheId, buildParams: RemoteManagedCacheBuildParams): EntriesFlow {
    val project = cacheId.projectId.findProjectOrNull()?.takeUnless { it.isDisposed } ?: return emptyFlow()
    return project.serviceAsync<RemoteManagedCacheManager>().create(cacheId, buildParams)
  }
}