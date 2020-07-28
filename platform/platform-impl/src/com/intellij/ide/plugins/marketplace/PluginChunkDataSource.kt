// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.marketplace

import com.intellij.ide.IdeBundle
import com.intellij.util.io.HttpRequests
import com.jetbrains.plugin.blockmap.core.BlockMap
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.Charset

private const val MAX_HTTP_HEADERS_LENGTH: Int = 5000
private const val MAX_STRING_LENGTH: Int = 1024

class PluginChunkDataSource(
  oldBlockMap: BlockMap,
  newBlockMap: BlockMap,
  private val newPluginUrl: String
) : Iterator<ByteArray> {
  private val oldMap = oldBlockMap.chunks.toSet()
  private val chunksIterator = newBlockMap.chunks.filter { chunk -> !oldMap.contains(chunk) }.iterator()
  private var curRangeChunkLengths = ArrayList<Int>()
  private var curChunkData = getRange(nextRange())
  private var pointer: Int = 0


  override fun hasNext(): Boolean {
    return curChunkData.size != 0
  }

  override fun next(): ByteArray {
    if (curChunkData.size != 0) {
      if (pointer < curChunkData.size) {
        return curChunkData[pointer++]
      }
      else {
        curChunkData = getRange(nextRange())
        pointer = 0
        return next()
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

  private fun getRange(range: String): ArrayList<ByteArray> {
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
          throw IOException(IdeBundle.message("wrong.http.range.responce",
                                              String(baos.toByteArray(), Charset.defaultCharset())))
        }
      }
      while (byte.toChar() != '\n')
      return String(baos.toByteArray(), Charset.defaultCharset())
    }
  }
}