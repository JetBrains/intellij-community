// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.cache.backend

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectCacheFileName
import com.intellij.platform.project.findProjectOrNull
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.KeyDescriptor
import com.intellij.util.io.PersistentMapBuilder
import com.intellij.util.io.cache.*
import kotlinx.coroutines.CoroutineScope
import java.io.DataInput
import java.io.DataOutput
import java.nio.file.Path

private fun remoteCacheLocation(): Path {
  return PathManager.getSystemDir().resolve("remote-cache")
}

@Service(Service.Level.PROJECT)
private class RemoteManagedCacheManager(private val project: Project, private val coroutineScope: CoroutineScope) {
  private val storage = ConcurrentCollectionFactory.createConcurrentMap<String, ManagedCache<ByteArray, ByteArray>>()
  fun get(cacheId: CacheId): ManagedCache<ByteArray, ByteArray> {
    return storage[cacheId.name]!!
  }
  suspend fun create(cacheId: CacheId, buildParams: RemoteManagedCacheBuildParams): List<PrefetchedRemoteCacheValue> {
    val path = getCacheDir(cacheId.name, project)
    val builder = PersistentMapBuilder.newBuilder(
      path,
      MyKeyDescriptor(),
      Externalizer(),
    ).withVersion(buildParams.serdeVersion)
    val cache = ManagedPersistentCache(cacheId.name, builder, coroutineScope)
    storage[cacheId.name] = cache
    return cache.entries().map { (key, value) -> PrefetchedRemoteCacheValue(key, value) }
  }

  private fun getCacheDir(cacheName: String, project: Project): Path {
    val projectPart = project.getProjectCacheFileName(hashSeparator = "")
    return remoteCacheLocation().resolve("$cacheName-$projectPart").resolve(cacheName)
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
  private suspend fun CacheId.cache() = projectId.findProjectOrNull()?.serviceAsync<RemoteManagedCacheManager>()?.get(this)
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

  override suspend fun createCacheAndProvideEntries(cacheId: CacheId, buildParams: RemoteManagedCacheBuildParams): List<PrefetchedRemoteCacheValue> {
    val project = cacheId.projectId.findProjectOrNull() ?: return emptyList()
    return project.serviceAsync<RemoteManagedCacheManager>().create(cacheId, buildParams)
  }
}