// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.io.fastCgi

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.Consumer
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.addChannelListener
import com.intellij.util.io.handler
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.handler.codec.http.*
import org.jetbrains.builtInWebServer.SingleConnectionNetService
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.doneRun
import org.jetbrains.concurrency.errorIfNotMessage
import org.jetbrains.io.*
import java.util.concurrent.atomic.AtomicInteger

internal val LOG = logger<FastCgiService>()

// todo send FCGI_ABORT_REQUEST if client channel disconnected
abstract class FastCgiService(project: Project) : SingleConnectionNetService(project) {
  private val requestIdCounter = AtomicInteger()
  private val requests = ContainerUtil.createConcurrentIntObjectMap<ClientInfo>()

  override fun configureBootstrap(bootstrap: Bootstrap, errorOutputConsumer: Consumer<String>) {
    bootstrap.handler {
      it.pipeline().addLast("fastCgiDecoder", FastCgiDecoder(errorOutputConsumer, this@FastCgiService))
      it.pipeline().addLast("exceptionHandler", ChannelExceptionHandler.getInstance())

      it.closeFuture().addChannelListener {
        requestIdCounter.set(0)
        if (!requests.isEmpty) {
          val waitingClients = requests.elements().toList()
          requests.clear()
          for (client in waitingClients) {
            sendBadGateway(client.channel, client.extraHeaders)
          }
        }
      }
    }
  }

  fun send(fastCgiRequest: FastCgiRequest, content: ByteBuf) {
    val notEmptyContent: ByteBuf?
    if (content.isReadable) {
      content.retain()
      notEmptyContent = content
      notEmptyContent.touch()
    }
    else {
      notEmptyContent = null
    }

    try {
      val promise: Promise<*>
      val handler = processHandler.resultIfFullFilled
      if (handler == null) {
        promise = processHandler.get()
      }
      else {
        val channel = processChannel.get()
        if (channel == null || !channel.isOpen) {
          // channel disconnected for some reason
          promise = connectAgain()
        }
        else {
          fastCgiRequest.writeToServerChannel(notEmptyContent, channel)
          return
        }
      }

      promise
        .doneRun { fastCgiRequest.writeToServerChannel(notEmptyContent, processChannel.get()!!) }
        .onError {
          LOG.errorIfNotMessage(it)
          handleError(fastCgiRequest, notEmptyContent)
        }
    }
    catch (e: Throwable) {
      LOG.error(e)
      handleError(fastCgiRequest, notEmptyContent)
    }
  }

  private fun handleError(fastCgiRequest: FastCgiRequest, content: ByteBuf?) {
    try {
      if (content != null && content.refCnt() != 0) {
        content.release()
      }
    }
    finally {
      requests.remove(fastCgiRequest.requestId)?.let {
        sendBadGateway(it.channel, it.extraHeaders)
      }
    }
  }

  fun allocateRequestId(channel: Channel, extraHeaders: HttpHeaders): Int {
    var requestId = requestIdCounter.getAndIncrement()
    if (requestId >= java.lang.Short.MAX_VALUE) {
      requestIdCounter.set(0)
      requestId = requestIdCounter.getAndDecrement()
    }
    requests.put(requestId, ClientInfo(channel, extraHeaders))
    return requestId
  }

  fun responseReceived(id: Int, buffer: ByteBuf?) {
    val client = requests.remove(id)
    if (client == null || !client.channel.isActive) {
      buffer?.release()
      return
    }

    val channel = client.channel
    if (buffer == null) {
      HttpResponseStatus.BAD_GATEWAY.send(channel)
      return
    }

    val httpResponse = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buffer)
    try {
      parseHeaders(httpResponse, buffer)
      httpResponse.addServer()
      if (!HttpUtil.isContentLengthSet(httpResponse)) {
        HttpUtil.setContentLength(httpResponse, buffer.readableBytes().toLong())
      }
      httpResponse.headers().add(client.extraHeaders)
    }
    catch (e: Throwable) {
      buffer.release()
      try {
        LOG.error(e)
      }
      finally {
        HttpResponseStatus.INTERNAL_SERVER_ERROR.send(channel)
      }
      return
    }

    channel.writeAndFlush(httpResponse)
  }
}

private fun sendBadGateway(channel: Channel, extraHeaders: HttpHeaders) {
  try {
    if (channel.isActive) {
      HttpResponseStatus.BAD_GATEWAY.send(channel, extraHeaders = extraHeaders)
    }
  }
  catch (e: Throwable) {
    NettyUtil.log(e, LOG)
  }
}

private fun parseHeaders(response: HttpResponse, buffer: ByteBuf) {
  val builder = StringBuilder()
  while (buffer.isReadable) {
    builder.setLength(0)

    var key: String? = null
    var valueExpected = true
    while (true) {
      val b = buffer.readByte().toInt()
      if (b < 0 || b.toChar() == '\n') {
        break
      }

      if (b.toChar() != '\r') {
        if (valueExpected && b.toChar() == ':') {
          valueExpected = false

          key = builder.toString()
          builder.setLength(0)
          MessageDecoder.skipWhitespace(buffer)
        }
        else {
          builder.append(b.toChar())
        }
      }
    }

    if (builder.isEmpty()) {
      // end of headers
      return
    }

    // skip standard headers
    if (key.isNullOrEmpty() || key!!.startsWith("http", ignoreCase = true) || key.startsWith("X-Accel-", ignoreCase = true)) {
      continue
    }

    val value = builder.toString()
    if (key.equals("status", ignoreCase = true)) {
      val index = value.indexOf(' ')
      if (index == -1) {
        LOG.warn("Cannot parse status: " + value)
        response.status = HttpResponseStatus.OK
      }
      else {
        response.status = HttpResponseStatus.valueOf(Integer.parseInt(value.substring(0, index)))
      }
    }
    else if (!(key.startsWith("http") || key.startsWith("HTTP"))) {
      response.headers().add(key, value)
    }
  }
}

private class ClientInfo(val channel: Channel, val extraHeaders: HttpHeaders)