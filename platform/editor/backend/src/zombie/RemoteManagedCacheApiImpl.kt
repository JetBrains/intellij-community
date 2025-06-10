// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.editor.backend.zombie

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.impl.zombie.necropolisPath
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectCacheFileName
import com.intellij.platform.editor.zombie.rpc.FingerprintedZombieDto
import com.intellij.platform.editor.zombie.rpc.RemoteManagedCacheApi
import com.intellij.platform.editor.zombie.rpc.ZombieCacheId
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
  private val storage = ConcurrentCollectionFactory.createConcurrentMap<String, ManagedCache<Int, FingerprintedZombieDto>>()
  fun get(zombieCacheId: ZombieCacheId): ManagedCache<Int, FingerprintedZombieDto> {
    return storage[zombieCacheId.name]!!
  }
  fun create(zombieCacheId: ZombieCacheId) {
    val (name, path) = cacheNameAndPath(zombieCacheId.name)
    val builder = PersistentMapBuilder.newBuilder(
      path,
      EnumeratorIntegerDescriptor.INSTANCE,
      Externalizer,
    )
    storage[zombieCacheId.name] = ManagedPersistentCache(name, builder, coroutineScope)
  }

  private fun cacheNameAndPath(graveName: String): Pair<String, Path> {
    // IJPL-157893 the cache should survive project renaming
    val projectName = project.getProjectCacheFileName(hashSeparator="-")
    val projectPath = necropolisPath().resolve("$projectName-backend")
    val cacheName = "$graveName-$projectName" // name should be unique across the application
    val cachePath = projectPath.resolve(graveName).resolve(graveName)
    return cacheName to cachePath
  }

  private object Externalizer : DataExternalizer<FingerprintedZombieDto> {
    override fun save(out: DataOutput, value: FingerprintedZombieDto) {
      out.writeLong(value.fingerprint)
      out.writeInt(value.zombie.size)
      out.write(value.zombie.toByteArray())
    }

    override fun read(`in`: DataInput): FingerprintedZombieDto {
      val fingerprint = `in`.readLong()
      val zombieSize = `in`.readInt()
      val zombie = ByteArray(zombieSize)
      `in`.readFully(zombie)
      return FingerprintedZombieDto(fingerprint, zombie.toList())
    }
  }
}

internal class RemoteManagedCacheApiImpl: RemoteManagedCacheApi {
  private suspend fun ZombieCacheId.cache() = projectId.findProjectOrNull()?.serviceAsync<RemoteManagedCacheManager>()?.get(this)
  override suspend fun get(grave: ZombieCacheId, zombieId: Int): FingerprintedZombieDto? {
    return grave.cache()?.get(zombieId)
  }

  override suspend fun put(grave: ZombieCacheId, zombieId: Int, zombie: FingerprintedZombieDto?) {
    grave.cache()?.let {
      if (zombie == null) {
        it.remove(zombieId)
      } else {
        it.put(zombieId, zombie)
      }
    }
  }

  override suspend fun create(grave: ZombieCacheId) {
    val project = grave.projectId.findProjectOrNull() ?: return
    project.serviceAsync<RemoteManagedCacheManager>().create(grave)
  }
}