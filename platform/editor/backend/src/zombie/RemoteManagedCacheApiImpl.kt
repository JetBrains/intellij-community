// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.editor.backend.zombie

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.impl.zombie.necropolisCacheNameAndPath
import com.intellij.openapi.project.Project
import com.intellij.platform.editor.zombie.rpc.CacheId
import com.intellij.platform.editor.zombie.rpc.PrefetchedRemoteCacheValue
import com.intellij.platform.editor.zombie.rpc.RemoteManagedCacheApi
import com.intellij.platform.editor.zombie.rpc.RemoteManagedCacheDto
import com.intellij.platform.project.findProjectOrNull
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.KeyDescriptor
import com.intellij.util.io.PersistentMapBuilder
import com.intellij.util.io.cache.ManagedCache
import com.intellij.util.io.cache.ManagedPersistentCache
import kotlinx.coroutines.CoroutineScope
import java.io.DataInput
import java.io.DataOutput

@Service(Service.Level.PROJECT)
private class RemoteManagedCacheManager(private val project: Project, private val coroutineScope: CoroutineScope) {
  private val storage = ConcurrentCollectionFactory.createConcurrentMap<String, ManagedCache<RemoteManagedCacheDto, RemoteManagedCacheDto>>()
  fun get(cacheId: CacheId): ManagedCache<RemoteManagedCacheDto, RemoteManagedCacheDto> {
    return storage[cacheId.name]!!
  }
  suspend fun create(cacheId: CacheId): List<PrefetchedRemoteCacheValue> {
    // RD and monolith caches could be handled, since Necropolis is loaded on backend too
    val (name, path) = necropolisCacheNameAndPath("${cacheId.name}-backend", project)
    val builder = PersistentMapBuilder.newBuilder(
      path,
      MyKeyDescriptor(),
      Externalizer(),
    ).withVersion(SERDE_VERSION)
    val cache = ManagedPersistentCache(name, builder, coroutineScope)
    storage[cacheId.name] = cache
    return cache.entries().map { (key, value) -> PrefetchedRemoteCacheValue(key, value) }
  }

  private open class Externalizer : DataExternalizer<RemoteManagedCacheDto> {
    override fun save(out: DataOutput, value: RemoteManagedCacheDto) {
      out.writeInt(value.data.size)
      out.write(value.data)
    }

    override fun read(`in`: DataInput): RemoteManagedCacheDto {
      val dataSize = `in`.readInt()
      val data = ByteArray(dataSize)
      `in`.readFully(data)
      return RemoteManagedCacheDto(data)
    }
  }

  private class MyKeyDescriptor : Externalizer(), KeyDescriptor<RemoteManagedCacheDto> {
    override fun getHashCode(value: RemoteManagedCacheDto): Int = value.data.contentHashCode()
    override fun isEqual(val1: RemoteManagedCacheDto?, val2: RemoteManagedCacheDto?): Boolean {
      return val1?.data?.contentEquals(val2?.data) ?: (val2 == null)
    }
  }

  companion object {
    private const val SERDE_VERSION = 1
  }
}

internal class RemoteManagedCacheApiImpl: RemoteManagedCacheApi {
  private suspend fun CacheId.cache() = projectId.findProjectOrNull()?.serviceAsync<RemoteManagedCacheManager>()?.get(this)
  override suspend fun get(cacheId: CacheId, key: RemoteManagedCacheDto): RemoteManagedCacheDto? {
    return cacheId.cache()?.get(key)
  }

  override suspend fun put(cacheId: CacheId, key: RemoteManagedCacheDto, value: RemoteManagedCacheDto?) {
    cacheId.cache()?.let {
      if (value == null) {
        it.remove(key)
      } else {
        it.put(key, value)
      }
    }
  }

  override suspend fun createPrefetchFlow(cacheId: CacheId): List<PrefetchedRemoteCacheValue> {
    val project = cacheId.projectId.findProjectOrNull() ?: return emptyList()
    return project.serviceAsync<RemoteManagedCacheManager>().create(cacheId)
  }
}