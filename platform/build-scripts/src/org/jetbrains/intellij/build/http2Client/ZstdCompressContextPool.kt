// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.http2Client

import com.github.luben.zstd.ZstdCompressCtx
import com.github.luben.zstd.ZstdDecompressCtx
import java.util.concurrent.ConcurrentLinkedQueue

// we cannot use Netty Recycler as we must close ZstdCompressCtx after use of pool
internal class ZstdCompressContextPool(private val level: Int = 3) : AutoCloseable {
  private val pool = ConcurrentLinkedQueue<ZstdCompressCtx>()

  inline fun <T> withZstd(task: (zstd: ZstdCompressCtx) -> T): T {
    val zstd = allocate()
    try {
      return task(zstd)
    }
    finally {
      zstd.reset()
      pool.offer(zstd)
    }
  }

  private fun allocate(): ZstdCompressCtx {
    pool.poll()?.let {
      configure(it)
      return it
    }

    val zstd = ZstdCompressCtx()
    configure(zstd)
    return zstd
  }

  private fun configure(zstd: ZstdCompressCtx) {
    zstd.setLevel(level)
    //zstd.setLong(64)
  }

  override fun close() {
    while (true) {
      (pool.poll() ?: return).close()
    }
  }
}

internal class ZstdDecompressContextPool : AutoCloseable {
  private val pool = ConcurrentLinkedQueue<ZstdDecompressCtx>()

  fun allocate(): ZstdDecompressCtx {
    pool.poll()?.let {
      return it
    }

    return ZstdDecompressCtx()
  }

  override fun close() {
    while (true) {
      (pool.poll() ?: return).close()
    }
  }

  fun release(zstd: ZstdDecompressCtx) {
    zstd.reset()
    pool.offer(zstd)
  }
}