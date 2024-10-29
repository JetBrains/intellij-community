// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.platform.ide.bootstrap

import com.intellij.util.lang.ZipFile
import com.intellij.util.lang.ZipFilePool
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.StampedLock

private const val STRIPE_COUNT = 64

@ApiStatus.Internal
class ZipFilePoolImpl : ZipFilePool() {
  private val pool = ConcurrentHashMap<Path, MyEntryResolver>(1024)

  private val mask = (1 shl (Integer.SIZE - Integer.numberOfLeadingZeros(STRIPE_COUNT - 1))) - 1
  private val locks = Array(mask + 1) { StampedLock() }

  private fun getLock(hash: Int): StampedLock {
    return locks[hash and mask]
  }

  override fun loadZipFile(file: Path): ZipFile {
    val resolver = pool.get(file)
    // doesn't make sense to use pool for requests from class loader (requested only once per class loader)
    return resolver?.zipFile ?: ZipFile.load(file)
  }

  override fun load(file: Path): EntryResolver {
    pool.get(file)?.let { return it }
    val lock = getLock(file.hashCode())
    val stamp = lock.writeLock()
    try {
      return pool.computeIfAbsent(file) {
        MyEntryResolver(ZipFile.load(file))
      }
    }
    finally {
      lock.unlockWrite(stamp)
    }
  }

  fun clear() {
    pool.clear()
  }
}

private class MyEntryResolver(@JvmField val zipFile: ZipFile) : ZipFilePool.EntryResolver {
  override fun loadZipEntry(path: String) = zipFile.getInputStream(path)

  override fun toString() = zipFile.toString()
}