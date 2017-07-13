package org.jetbrains.io.fastCgi

import com.intellij.openapi.util.io.FileUtil
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.ByteBufUtil
import io.netty.channel.Channel
import io.netty.handler.codec.http.FullHttpRequest
import org.jetbrains.builtInWebServer.PathInfo
import org.jetbrains.io.serverHeaderValue
import java.net.InetSocketAddress
import java.nio.CharBuffer
import java.util.*

private val PARAMS = 4
private val BEGIN_REQUEST = 1
private val RESPONDER = 1
private val FCGI_KEEP_CONNECTION = 1
private val STDIN = 5
private val VERSION = 1

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

  private fun writeParamLength(l: Int) {
    if (l < 128) {
      params.writeByte(l)
    }
    else {
      params.writeByte(128 or (l shr 24))
      params.writeByte(l shr 16)
      params.writeByte(l shr 8)
      params.writeByte(l)
    }
  }

  fun writeHeaders(request: FullHttpRequest, clientChannel: Channel) {
    addHeader("REQUEST_URI", request.uri())
    addHeader("REQUEST_METHOD", request.method().name())

    val remote = clientChannel.remoteAddress() as InetSocketAddress
    addHeader("REMOTE_ADDR", remote.address.hostAddress)
    addHeader("REMOTE_PORT", Integer.toString(remote.port))

    val local = clientChannel.localAddress() as InetSocketAddress
    addHeader("SERVER_SOFTWARE", serverHeaderValue)
    addHeader("SERVER_NAME", serverHeaderValue)

    addHeader("SERVER_ADDR", local.address.hostAddress)
    addHeader("SERVER_PORT", Integer.toString(local.port))

    addHeader("GATEWAY_INTERFACE", "CGI/1.1")
    addHeader("SERVER_PROTOCOL", request.protocolVersion().text())

    // PHP only, required if PHP was built with --enable-force-cgi-redirect
    addHeader("REDIRECT_STATUS", "200")

    var queryString = ""
    val queryIndex = request.uri().indexOf('?')
    if (queryIndex != -1) {
      queryString = request.uri().substring(queryIndex + 1)
    }
    addHeader("QUERY_STRING", queryString)

    addHeader("CONTENT_LENGTH", request.content().readableBytes().toString())

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

    var releaseContent = content != null
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

      if (content != null) {
        writeHeader(buffer, STDIN, content.readableBytes())
      }

      fastCgiChannel.write(buffer)

      if (content != null) {
        fastCgiChannel.write(content)
        // channel.write releases
        releaseContent = false

        val headerBuffer = fastCgiChannel.alloc().ioBuffer(HEADER_LENGTH, HEADER_LENGTH)
        writeHeader(headerBuffer, STDIN, 0)
        fastCgiChannel.write(headerBuffer)
      }
    }
    finally {
      if (releaseContent) {
        assert(content != null)
        content!!.release()
      }
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