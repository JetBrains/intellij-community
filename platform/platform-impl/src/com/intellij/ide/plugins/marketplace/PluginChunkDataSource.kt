// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.marketplace

import com.intellij.ide.IdeBundle
import com.intellij.util.io.HttpRequests
import com.jetbrains.plugin.blockmap.core.BlockMap
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.Charset

// According to Amazon CloudFront documentation the maximum length of a request,
// including the path, the query string (if any), and headers, is 20,480 bytes.
private const val MAX_HTTP_HEADERS_LENGTH: Int = 19500
private const val MAX_STRING_LENGTH: Int = 1024

class PluginChunkDataSource(
  oldBlockMap: BlockMap,
  newBlockMap: BlockMap,
  private val newPluginUrl: String
) : Iterator<ByteArray> {
  private val oldSet = oldBlockMap.chunks.toSet()
  private val chunksIterator = newBlockMap.chunks.filter { chunk -> !oldSet.contains(chunk) }.iterator()
  private var curRangeChunkLengths = ArrayList<Int>()
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
    curRangeChunkLengths.clear()
    var rangeHeaderLength = 0
    while (chunksIterator.hasNext() && range.length <= MAX_HTTP_HEADERS_LENGTH) {
      val newChunk = chunksIterator.next()
      range.append("${newChunk.offset}-${newChunk.offset + newChunk.length - 1},")
      curRangeChunkLengths.add(newChunk.length)
      rangeHeaderLength += newChunk.length
    }
    return range.removeSuffix(",").toString()
  }

  private fun getRange(range: String): MutableList<ByteArray> {
    val result = ArrayList<ByteArray>()
    HttpRequests.requestWithRange(newPluginUrl, range).productNameAsUserAgent().connect { request ->
      val boundary = request.connection.contentType.removePrefix("multipart/byteranges; boundary=")
      request.inputStream.buffered().use { input ->
        for (length in curRangeChunkLengths) {
          // parsing http get range response
          do {
            val str = nextLine(input)
          }
          while (!str.contains(boundary))
          // skip useless lines: Content-Type, Content-Length and empty line
          nextLine(input)
          nextLine(input)
          nextLine(input)
          val data = ByteArray(length)
          for (i in 0 until length) data[i] = input.read().toByte()
          result.add(data)
        }
      }
    }
    return result
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