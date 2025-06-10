// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.editor.frontend.zombie

import com.intellij.openapi.editor.impl.zombie.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectCacheFileName
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.DataInputOutputUtil.readLONG
import com.intellij.util.io.DataInputOutputUtil.writeLONG
import com.intellij.util.io.EnumeratorIntegerDescriptor
import com.intellij.util.io.PersistentMapBuilder
import com.intellij.util.io.cache.ManagedPersistentCache
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.io.DataInput
import java.io.DataOutput
import java.nio.file.Path

@ApiStatus.Internal
class ManagedCacheZombieCacheFactory<Z: Zombie>: ZombieCacheFactory<Z> {
  override fun createCache(graveName: String, necromancy: Necromancy<Z>, project: Project, coroutineScope: CoroutineScope): Cache<Z> {
    val (cacheName, cachePath) = cacheNameAndPath(graveName, project)
    val builder = PersistentMapBuilder.newBuilder(
      cachePath,
      EnumeratorIntegerDescriptor.INSTANCE,
      FingerprintedExternalizer(necromancy),
    ).withVersion(necromancy.spellLevel())
    return if (necromancy.isDeepBury()) {
      ManagedPersistentCache(graveName, builder, coroutineScope)
    } else {
      // TODO: heap implementation
      ManagedPersistentCache(cacheName, builder, coroutineScope)
    }
  }

  private fun cacheNameAndPath(graveName: String, project: Project): Pair<String, Path> {
    // IJPL-157893 the cache should survive project renaming
    val projectName = project.getProjectCacheFileName(hashSeparator="-")
    val projectPath = necropolisPath().resolve(projectName)
    val cacheName = "$graveName-$projectName" // name should be unique across the application
    val cachePath = projectPath.resolve(graveName).resolve(graveName)
    return cacheName to cachePath
  }
}

private class FingerprintedExternalizer<Z : Zombie>(
  private val necromancy: Necromancy<Z>,
) : DataExternalizer<FingerprintedZombie<Z>> {

  override fun read(input: DataInput): FingerprintedZombie<Z> {
    val fingerprint = readLONG(input)
    val zombie = necromancy.exhumeZombie(input)
    return FingerprintedZombieImpl(fingerprint, zombie)
  }

  override fun save(output: DataOutput, value: FingerprintedZombie<Z>) {
    writeLONG(output, value.fingerprint())
    necromancy.buryZombie(output, value.zombie())
  }
}