// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.marketplace

import com.intellij.ide.IdeBundle
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.io.HttpRequests
import com.jetbrains.plugin.blockmap.core.BlockMap
import com.jetbrains.plugin.blockmap.core.Chunk
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.Charset

private const val MAX_HTTP_HEADERS_LENGTH: Int = 19000
private const val MAX_RANGE_BYTES: Int = 10_000_000
private const val MAX_STRING_LENGTH: Int = 1024

private val LOG = logger<PluginChunkDataSource>()

class PluginChunkDataSource(
  oldBlockMap: BlockMap,
  newBlockMap: BlockMap,
  private val newPluginUrl: String
) : Iterator<ByteArray> {
  private val oldSet = oldBlockMap.chunks.toSet()
  private val chunks = newBlockMap.chunks.filter { chunk -> !oldSet.contains(chunk) }
  private var pos = 0
  private var chunkSequences = ArrayList<MutableList<Chunk>>()
  private var curChunkData = getRange(nextRange())
  private var pointer: Int = 0


  override fun hasNext() = curChunkData.size != 0

  override fun next(): ByteArray {
    return if (curChunkData.size != 0) {
      if (pointer < curChunkData.size) {
        curChunkData[pointer++]
      }
      else {
        curChunkData = getRange(nextRange())
        pointer = 0
        next()
      }
    }
    else throw NoSuchElementException()
  }

  private fun nextRange(): String {
    val range = StringBuilder()
    chunkSequences.clear()
    var bytes = 0
    while (pos < chunks.size
           && range.length < MAX_HTTP_HEADERS_LENGTH
           && bytes < MAX_RANGE_BYTES) {
      val chunkSequence = nextChunkSequence(bytes)
      chunkSequences.add(chunkSequence)
      bytes += chunkSequence.last().offset + chunkSequence.last().length - chunkSequence[0].offset
      range.append("${chunkSequence[0].offset}-${chunkSequence.last().offset + chunkSequence.last().length - 1},")
    }
    return range.removeSuffix(",").toString()
  }

  private fun nextChunkSequence(bytes: Int): MutableList<Chunk> {
    val result = ArrayList<Chunk>()
    result.add(chunks[pos])
    pos++
    var sum = result[0].length
    while (pos < chunks.size - 1
           && chunks[pos].offset == chunks[pos - 1].offset + chunks[pos - 1].length
           && sum + bytes < MAX_RANGE_BYTES) {
      result.add(chunks[pos])
      sum += chunks[pos].length
      pos++
    }
    return result
  }

  private fun getRange(range: String): MutableList<ByteArray> {
    val result = ArrayList<ByteArray>()
    if (range.isEmpty()) {
      LOG.warn("Empty range: request '$newPluginUrl' range '$range'")
    }
    else {
      HttpRequests
        .requestWithRange(newPluginUrl, range)
        .productNameAsUserAgent()
        .setHeadersViaTuner()
        .connect { request ->
          val boundary = request.connection.contentType.removePrefix("multipart/byteranges; boundary=")
          request.inputStream.buffered().use { input ->
            when {
              chunkSequences.size > 1 -> {
                for (sequence in chunkSequences) {
                  parseHttpMultirangeHeaders(input, boundary)
                  parseHttpRangeBody(input, sequence, result)
                }
              }
              chunkSequences.size == 1 -> parseHttpRangeBody(input, chunkSequences[0], result)
              else -> {
                LOG.warn("Zero chunks: request '$newPluginUrl' range '$range'")
              }
            }
          }
        }
    }
    return result
  }

  private fun parseHttpMultirangeHeaders(input: BufferedInputStream, boundary: String) {
    val openingEmptyLine = nextLine(input)
    if (openingEmptyLine.trim().isNotEmpty()) {
      throw IOException(IdeBundle.message("http.multirange.response.doesnt.include.line.separator"))
    }
    val boundaryLine = nextLine(input)
    if (!boundaryLine.contains(boundary)) {
      throw IOException(IdeBundle.message("http.multirange.response.doesnt.contain.boundary", boundaryLine, boundary))
    }
    val contentTypeLine = nextLine(input)
    if (!contentTypeLine.startsWith("Content-Type")) {
      throw IOException(IdeBundle.message("http.multirange.response.includes.incorrect.header", contentTypeLine, "Content-Type"))
    }
    val contentRangeLine = nextLine(input)
    if (!contentRangeLine.startsWith("Content-Range")) {
      throw IOException(IdeBundle.message("http.multirange.response.includes.incorrect.header", contentRangeLine, "Content-Range"))
    }
    val closingEmptyLine = nextLine(input)
    if (closingEmptyLine.trim().isNotEmpty()) {
      throw IOException(IdeBundle.message("http.multirange.response.doesnt.include.line.separator"))
    }
  }

  private fun parseHttpRangeBody(
    input: BufferedInputStream,
    sequence: MutableList<Chunk>,
    result: MutableList<ByteArray>
  ) {
    for (chunk in sequence) {
      val data = ByteArray(chunk.length)
      for (i in 0 until chunk.length) data[i] = input.read().toByte()
      result.add(data)
    }
  }

  private fun nextLine(input: BufferedInputStream): String {
    ByteArrayOutputStream().use { baos ->
      do {
        val byte = input.read()
        baos.write(byte)
        if (baos.size() >= MAX_STRING_LENGTH) {
          throw IOException(IdeBundle.message("wrong.http.range.response", String(baos.toByteArray(), Charset.defaultCharset())))
        }
      }
      while (byte.toChar() != '\n')
      return String(baos.toByteArray(), Charset.defaultCharset())
    }
  }
}