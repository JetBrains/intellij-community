// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.marketplace

import com.intellij.util.io.HttpRequests
import com.jetbrains.plugin.blockmap.core.BlockMap
import java.io.BufferedInputStream
import java.nio.charset.Charset

private const val MAX_HTTP_HEADERS_LENGTH : Int = 5000

class PluginChunkDataSource(
  oldBlockMap: BlockMap,
  newBlockMap: BlockMap,
  private val newPluginUrl : String
) : Iterator<ByteArray>{
  private val oldMap = oldBlockMap.chunks.toSet()
  private val newChunks = newBlockMap.chunks.filter { chunk -> !oldMap.contains(chunk) }
  private var curChunk = 0
  private var curRangeChunkLengths = ArrayList<Int>()
  private var curChunkData = getRange(nextRange())
  private var pointer = 0
  private val byteBuffer = ByteArray(1024)

  override fun hasNext(): Boolean {
    return curChunkData.size != 0
  }

  override fun next(): ByteArray {
    if(curChunkData.size != 0){
      if(pointer < curChunkData.size){
        return curChunkData[pointer++]
      }else{
        curChunkData = getRange(nextRange())
        pointer = 0
        return next()
      }
    }else throw NoSuchElementException()
  }

  private fun nextRange() : String{
    val range = StringBuilder()
    curRangeChunkLengths = ArrayList()
    var size = 0
    while (curChunk < newChunks.size && range.length <= MAX_HTTP_HEADERS_LENGTH) {
      val newChunk = newChunks[curChunk]
      range.append("${newChunk.offset}-${newChunk.offset + newChunk.length - 1},")
      curRangeChunkLengths.add(newChunk.length)
      size+=newChunk.length
      curChunk++
    }
    return range.removeSuffix(",").toString()
  }

  private fun getRange(range : String) : ArrayList<ByteArray>{
    val result = ArrayList<ByteArray>()
    HttpRequests.requestWithRange(newPluginUrl, range).productNameAsUserAgent().connect { request ->
      val boundary = request.connection.contentType.removePrefix("multipart/byteranges; boundary=")
      request.inputStream.buffered().use { input ->
        for(length in curRangeChunkLengths){
          // parsing http get range response
          do{
            val str = nextLine(input)
          } while(!str.contains(boundary))
          nextLine(input)
          nextLine(input)
          nextLine(input)
          val data = ByteArray(length)
          for(i in 0 until length) data[i] = input.read().toByte()
          result.add(data)
        }
      }
    }
    return result
  }

  private fun nextLine(input : BufferedInputStream) : String{
    var i = 0
    do {
      byteBuffer[i] = input.read().toByte()
      i++
    }
    while (byteBuffer[i - 1].toChar() != '\n')
    return String(byteBuffer, 0, i, Charset.defaultCharset())
  }
}