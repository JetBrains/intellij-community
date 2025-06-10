// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.editor.backend.zombie

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.impl.zombie.necropolisPath
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectCacheFileName
import com.intellij.platform.editor.zombie.rpc.CacheId
import com.intellij.platform.editor.zombie.rpc.RemoteManagedCacheApi
import com.intellij.platform.editor.zombie.rpc.RemoteManagedCacheValueDto
import com.intellij.platform.project.findProjectOrNull
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorIntegerDescriptor
import com.intellij.util.io.PersistentMapBuilder
import com.intellij.util.io.cache.ManagedCache
import com.intellij.util.io.cache.ManagedPersistentCache
import kotlinx.coroutines.CoroutineScope
import java.io.DataInput
import java.io.DataOutput
import java.nio.file.Path

@Service(Service.Level.PROJECT)
private class RemoteManagedCacheManager(private val project: Project, private val coroutineScope: CoroutineScope) {
  private val storage = ConcurrentCollectionFactory.createConcurrentMap<String, ManagedCache<Int, RemoteManagedCacheValueDto>>()
  fun get(cacheId: CacheId): ManagedCache<Int, RemoteManagedCacheValueDto> {
    return storage[cacheId.name]!!
  }
  fun create(cacheId: CacheId) {
    val (name, path) = cacheNameAndPath(cacheId.name)
    val builder = PersistentMapBuilder.newBuilder(
      path,
      EnumeratorIntegerDescriptor.INSTANCE,
      Externalizer,
    ).withVersion(SERDE_VERSION)
    storage[cacheId.name] = ManagedPersistentCache(name, builder, coroutineScope)
  }

  private fun cacheNameAndPath(cacheType: String): Pair<String, Path> {
    // IJPL-157893 the cache should survive project renaming
    val projectName = project.getProjectCacheFileName(hashSeparator="-")
    val projectPath = necropolisPath().resolve("$projectName-backend")
    val cacheName = "$cacheType-$projectName" // name should be unique across the application
    val cachePath = projectPath.resolve(cacheType).resolve(cacheType)
    return cacheName to cachePath
  }

  private object Externalizer : DataExternalizer<RemoteManagedCacheValueDto> {
    override fun save(out: DataOutput, value: RemoteManagedCacheValueDto) {
      out.writeLong(value.fingerprint)
      out.writeInt(value.data.size)
      out.write(value.data.toByteArray())
    }

    override fun read(`in`: DataInput): RemoteManagedCacheValueDto {
      val fingerprint = `in`.readLong()
      val dataSize = `in`.readInt()
      val data = ByteArray(dataSize)
      `in`.readFully(data)
      return RemoteManagedCacheValueDto(fingerprint, data.toList())
    }
  }
  companion object {
    private const val SERDE_VERSION = 0
  }
}

internal class RemoteManagedCacheApiImpl: RemoteManagedCacheApi {
  private suspend fun CacheId.cache() = projectId.findProjectOrNull()?.serviceAsync<RemoteManagedCacheManager>()?.get(this)
  override suspend fun get(cacheId: CacheId, key: Int): RemoteManagedCacheValueDto? {
    return cacheId.cache()?.get(key)
  }

  override suspend fun put(cacheId: CacheId, key: Int, value: RemoteManagedCacheValueDto?) {
    cacheId.cache()?.let {
      if (value == null) {
        it.remove(key)
      } else {
        it.put(key, value)
      }
    }
  }

  override suspend fun create(cacheId: CacheId) {
    val project = cacheId.projectId.findProjectOrNull() ?: return
    project.serviceAsync<RemoteManagedCacheManager>().create(cacheId)
  }
}