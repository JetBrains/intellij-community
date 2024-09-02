// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.http2Client

import com.github.luben.zstd.ZstdCompressCtx
import com.github.luben.zstd.ZstdDecompressCtx
import io.netty.util.concurrent.FastThreadLocal
import java.util.concurrent.ConcurrentLinkedQueue

// we cannot use Netty Recycler as we must close ZstdCompressCtx after use of pool
internal class ZstdCompressContextPool : AutoCloseable {
  private val pool = object : FastThreadLocal<MutableList<ZstdCompressCtx>>() {
    override fun initialValue(): MutableList<ZstdCompressCtx> = ArrayList()

    override fun onRemoval(value: MutableList<ZstdCompressCtx>) {
      for (context in value) {
        context.close()
      }
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
    zstd.setLevel(9)
    zstd.setPledgedSrcSize(contentSize)
    zstd.setLong(27)
    return zstd
  }

  override fun close() {
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