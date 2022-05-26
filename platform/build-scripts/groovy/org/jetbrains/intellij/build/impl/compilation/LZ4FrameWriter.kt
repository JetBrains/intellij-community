// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")
package org.jetbrains.intellij.build.impl.compilation

import net.jpountz.lz4.LZ4Compressor
import net.jpountz.xxhash.XXHash32
import net.jpountz.xxhash.XXHashFactory
import okio.BufferedSink
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import kotlin.experimental.or

private const val MAGIC = 0x184D2204
private const val LZ4_MAX_HEADER_LENGTH = 4 +  // magic
                                          1 +  // FLG
                                          1 +  // BD
                                          8 +  // Content Size
                                          1 // HC
private const val LZ4_FRAME_INCOMPRESSIBLE_MASK = 0x80000000.toInt()

internal class LZ4FrameWriter(private val out: BufferedSink, blockSize: BlockSize,
                              knownSize: Long,
                              private val compressor: LZ4Compressor,
                              checksum: XXHash32) {
  internal enum class BlockSize(val indicator: Int) {
    SIZE_64KB(4), SIZE_256KB(5), SIZE_1MB(6), SIZE_4MB(7);

    companion object {
      fun valueOf(indicator: Int): BlockSize {
        return when (indicator) {
          7 -> SIZE_4MB
          6 -> SIZE_1MB
          5 -> SIZE_256KB
          4 -> SIZE_64KB
          else -> throw IllegalArgumentException("Block size must be 4-7. Cannot use value of $indicator")
        }
      }
    }
  }

  // buffer for uncompressed input data
  @JvmField
  val buffer: ByteBuffer

  // only allocated once so it can be reused
  private val compressedBuffer: ByteArray
  private val maxBlockSize: Int
  private val frameInfo: FrameInfo

  init {
    frameInfo = FrameInfo(FLG(FLG.DEFAULT_VERSION, FLG.Bits.BLOCK_INDEPENDENCE, FLG.Bits.CONTENT_SIZE), BD(blockSize))
    maxBlockSize = frameInfo.bD.blockMaximumSize
    buffer = ByteBuffer.allocate(maxBlockSize).order(ByteOrder.LITTLE_ENDIAN)
    compressedBuffer = ByteArray(this.compressor.maxCompressedLength(maxBlockSize))
    if (frameInfo.fLG.isEnabled(FLG.Bits.CONTENT_SIZE) && knownSize < 0) {
      throw IllegalArgumentException("Known size must be greater than zero in order to use the known size feature")
    }
    writeHeader(frameInfo, knownSize, out, checksum)
  }

  /**
   * Compresses buffered data, optionally computes an XXHash32 checksum, and writes the result.
   */
  fun writeBlock() {
    if (buffer.position() == 0) {
      return
    }

    // make sure there's no stale data
    Arrays.fill(compressedBuffer, 0.toByte())
    if (frameInfo.isEnabled(FLG.Bits.CONTENT_CHECKSUM)) {
      frameInfo.updateStreamHash(buffer.array(), 0, buffer.position())
    }
    var compressedLength = compressor.compress(buffer.array(), 0, buffer.position(), compressedBuffer, 0)
    val bufferToWrite: ByteArray
    val compressMethod: Int
    // store block uncompressed if compressed length is greater (incompressible)
    if (compressedLength >= buffer.position()) {
      compressedLength = buffer.position()
      bufferToWrite = Arrays.copyOf(buffer.array(), compressedLength)
      compressMethod = LZ4_FRAME_INCOMPRESSIBLE_MASK
    }
    else {
      bufferToWrite = compressedBuffer
      compressMethod = 0
    }

    // write content
    out.writeIntLe(compressedLength or compressMethod)
    out.write(bufferToWrite, 0, compressedLength)
    buffer.rewind()
  }

  fun finish() {
    assert(buffer.position() == 0)
    out.writeIntLe(0)
  }

  internal class FLG(val version: Int, vararg bits: Bits) {
    companion object {
      const val DEFAULT_VERSION = 1
    }

    private val bitSet = BitSet(8)

    internal enum class Bits(val position: Int) {
      RESERVED_0(0), RESERVED_1(1), CONTENT_CHECKSUM(2), CONTENT_SIZE(3), BLOCK_INDEPENDENCE(5);
    }

    init {
      for (bit in bits) {
        bitSet.set(bit.position)
      }
      validate()
    }

    fun toByte(): Byte {
      return (bitSet.toByteArray().get(0) or ((version and 3 shl 6).toByte()))
    }

    private fun validate() {
      if (bitSet.get(Bits.RESERVED_0.position)) {
        throw RuntimeException("Reserved0 field must be 0")
      }
      if (bitSet.get(Bits.RESERVED_1.position)) {
        throw RuntimeException("Reserved1 field must be 0")
      }
      if (!bitSet.get(Bits.BLOCK_INDEPENDENCE.position)) {
        throw RuntimeException("Dependent block stream is unsupported (BLOCK_INDEPENDENCE must be set)")
      }
      if (version != DEFAULT_VERSION) {
        throw RuntimeException("Version $version is unsupported")
      }
    }

    fun isEnabled(bit: Bits) = bitSet.get(bit.position)
  }

  internal class BD(private val blockSizeValue: BlockSize) {
    // 2^(2n+8)
    val blockMaximumSize: Int
      get() = 1 shl 2 * blockSizeValue.indicator + 8

    fun toByte() = (blockSizeValue.indicator and 7 shl 4).toByte()
  }

  internal class FrameInfo(val fLG: FLG, val bD: BD) {
    private val streamHash = if (fLG.isEnabled(FLG.Bits.CONTENT_CHECKSUM)) XXHashFactory.fastestInstance().newStreamingHash32(0) else null

    fun isEnabled(bit: FLG.Bits) = fLG.isEnabled(bit)

    fun updateStreamHash(buff: ByteArray?, off: Int, len: Int) {
      streamHash!!.update(buff, off, len)
    }
  }
}

private fun writeHeader(frameInfo: LZ4FrameWriter.FrameInfo, knownSize: Long, out: BufferedSink, checksum: XXHash32) {
  val headerBuffer = ByteBuffer.allocate(LZ4_MAX_HEADER_LENGTH).order(ByteOrder.LITTLE_ENDIAN)
  headerBuffer.putInt(MAGIC)
  headerBuffer.put(frameInfo.fLG.toByte())
  headerBuffer.put(frameInfo.bD.toByte())
  if (frameInfo.isEnabled(LZ4FrameWriter.FLG.Bits.CONTENT_SIZE)) {
    headerBuffer.putLong(knownSize)
  }
  // compute checksum on all descriptor fields
  val hash = checksum.hash(headerBuffer.array(), Integer.BYTES, headerBuffer.position() - Integer.BYTES, 0) shr 8 and 0xFF
  headerBuffer.put(hash.toByte())
  headerBuffer.flip()
  // write out frame descriptor
  out.write(headerBuffer)
}