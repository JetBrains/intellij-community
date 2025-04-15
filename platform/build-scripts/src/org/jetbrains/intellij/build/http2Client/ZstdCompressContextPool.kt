// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.http2Client

import com.github.luben.zstd.ZstdCompressCtx
import com.github.luben.zstd.ZstdDecompressCtx
import io.netty.util.concurrent.FastThreadLocal

// we cannot use Netty Recycler as we must close ZstdCompressCtx after use of pool
internal class ZstdCompressContextPool(private val level: Int = 12) {
  private val pool = object : FastThreadLocal<MutableList<ZstdCompressCtx>>() {
    override fun initialValue(): MutableList<ZstdCompressCtx> = ArrayList()

    override fun onRemoval(value: MutableList<ZstdCompressCtx>) {
      for (context in value) {
        context.close()
      }
      value.clear()
    }
  }

  inline fun <T> withZstd(contentSize: Long, task: (zstd: ZstdCompressCtx) -> T): T {
    val zstd = allocate(contentSize)
    try {
      return task(zstd)
    }
    finally {
      zstd.reset()
      pool.get().add(zstd)
    }
  }

  private fun allocate(contentSize: Long): ZstdCompressCtx {
    val zstd = pool.get().removeLastOrNull() ?: ZstdCompressCtx()
    zstd.setLevel(level)
    if (contentSize != -1L) {
      zstd.setPledgedSrcSize(contentSize)
    }
    zstd.setLong(27)
    return zstd
  }
}

internal class ZstdDecompressContextPool {
  private val pool = object : FastThreadLocal<MutableList<ZstdDecompressCtx>>() {
    override fun initialValue(): MutableList<ZstdDecompressCtx> = ArrayList()

    override fun onRemoval(value: MutableList<ZstdDecompressCtx>) {
      for (context in value) {
        context.close()
      }
      value.clear()
    }
  }

  fun allocate(): ZstdDecompressCtx {
    return pool.get().removeLastOrNull() ?: ZstdDecompressCtx()
  }

  fun release(zstd: ZstdDecompressCtx) {
    zstd.reset()
    pool.get().add(zstd)
  }
}