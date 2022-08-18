// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.io.fastCgi

import com.intellij.openapi.util.io.FileUtil
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.ByteBufUtil
import io.netty.channel.Channel
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpHeaderNames
import org.jetbrains.builtInWebServer.PathInfo
import org.jetbrains.io.serverHeaderValue
import java.net.InetSocketAddress
import java.nio.CharBuffer
import java.util.*
import kotlin.math.min

private const val PARAMS = 4
private const val BEGIN_REQUEST = 1
private const val RESPONDER = 1
private const val FCGI_KEEP_CONNECTION = 1
private const val STDIN = 5
private const val VERSION = 1

private const val MAX_CONTENT_LENGTH = 0xFFFF

class FastCgiRequest(val requestId: Int, allocator: ByteBufAllocator) {
  private val params = allocator.ioBuffer(4096)

  fun writeFileHeaders(pathInfo: PathInfo, canonicalRequestPath: CharSequence) {
    val root = pathInfo.root
    addHeader("DOCUMENT_ROOT", FileUtil.toSystemDependentName(root.path))
    addHeader("SCRIPT_FILENAME", pathInfo.filePath)
    addHeader("SCRIPT_NAME", canonicalRequestPath)
  }

  private fun addHeader(key: String, value: CharSequence?) {
    if (value == null) {
      return
    }

    val valBytes = (value as? String)?.toByteArray()
    val valBuffer = if (value is String) null else Charsets.UTF_8.encode(CharBuffer.wrap(value))

    val keyLength = key.length
    val valLength = valBytes?.size ?: valBuffer!!.limit()

    writeParamLength(keyLength)
    writeParamLength(valLength)

    ByteBufUtil.writeAscii(params, key)
    if (valBuffer == null) {
      params.writeBytes(valBytes)
    }
    else {
      params.writeBytes(valBuffer)
    }
  }

  private fun writeParamLength(length: Int) {
    if (length > 127) {
      params.writeInt(length or 0x80000000.toInt())
    }
    else {
      params.writeByte(length)
    }
  }

  fun writeHeaders(request: FullHttpRequest, clientChannel: Channel) {
    addHeader("REQUEST_URI", request.uri())
    addHeader("REQUEST_METHOD", request.method().name())

    val remote = clientChannel.remoteAddress() as InetSocketAddress
    addHeader("REMOTE_ADDR", remote.address.hostAddress)
    addHeader("REMOTE_PORT", remote.port.toString())

    val local = clientChannel.localAddress() as InetSocketAddress
    addHeader("SERVER_NAME", serverHeaderValue)

    addHeader("SERVER_ADDR", local.address.hostAddress)
    addHeader("SERVER_PORT", local.port.toString())

    addHeader("GATEWAY_INTERFACE", "CGI/1.1")
    addHeader("SERVER_PROTOCOL", request.protocolVersion().text())

    // PHP only, required if PHP was built with --enable-force-cgi-redirect
    addHeader("REDIRECT_STATUS", "200")

    var queryString = ""
    val queryIndex = request.uri().indexOf('?')
    if (queryIndex != -1) {
      queryString = request.uri().substring(queryIndex + 1)
      addHeader("DOCUMENT_URI", request.uri().substring(0, queryIndex))
    }
    else {
      addHeader("DOCUMENT_URI", request.uri())
    }
    addHeader("QUERY_STRING", queryString)

    addHeader("CONTENT_LENGTH", request.content().readableBytes().toString())
    addHeader("CONTENT_TYPE", request.headers().get(HttpHeaderNames.CONTENT_TYPE) ?: "")

    for ((key, value) in request.headers().iteratorAsString()) {
      if (!key.equals("keep-alive", ignoreCase = true) && !key.equals("connection", ignoreCase = true)) {
        addHeader("HTTP_${key.replace('-', '_').toUpperCase(Locale.ENGLISH)}", value)
      }
    }
  }

  // https://stackoverflow.com/questions/27457543/php-cgi-post-empty
  fun writeToServerChannel(content: ByteBuf?, fastCgiChannel: Channel) {
    if (fastCgiChannel.pipeline().first() == null) {
      throw IllegalStateException("No handler in the pipeline")
    }

    try {
      val buffer = fastCgiChannel.alloc().ioBuffer(4096)
      writeHeader(buffer, BEGIN_REQUEST, HEADER_LENGTH)
      buffer.writeShort(RESPONDER)
      buffer.writeByte(FCGI_KEEP_CONNECTION)
      // reserved[5]
      buffer.writeZero(5)

      writeHeader(buffer, PARAMS, params.readableBytes())
      buffer.writeBytes(params)

      writeHeader(buffer, PARAMS, 0)

      fastCgiChannel.write(buffer)

      if (content != null) {
        var position = content.readerIndex()
        var toWrite = content.readableBytes()
        while (toWrite > 0) {
          val length = min(MAX_CONTENT_LENGTH, toWrite)

          val headerBuffer = fastCgiChannel.alloc().ioBuffer(HEADER_LENGTH, HEADER_LENGTH)
          writeHeader(headerBuffer, STDIN, length)
          fastCgiChannel.write(headerBuffer)

          val chunk = content.slice(position, length)
          // channel.write releases
          chunk.retain()
          fastCgiChannel.write(chunk)
          toWrite -= length
          position += length
        }

        val headerBuffer = fastCgiChannel.alloc().ioBuffer(HEADER_LENGTH, HEADER_LENGTH)
        writeHeader(headerBuffer, STDIN, 0)
        fastCgiChannel.write(headerBuffer)
      }
    }
    finally {
      content?.release()
    }

    fastCgiChannel.flush()
  }

  private fun writeHeader(buffer: ByteBuf, type: Int, length: Int) {
    buffer.writeByte(VERSION)
    buffer.writeByte(type)
    buffer.writeShort(requestId)
    buffer.writeShort(length)
    // paddingLength, reserved
    buffer.writeZero(2)
  }
}