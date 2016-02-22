/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.io.fastCgi

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.Consumer
import com.intellij.util.containers.ConcurrentIntObjectMap
import com.intellij.util.containers.ContainerUtil
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.handler.codec.http.*
import org.jetbrains.builtInWebServer.SingleConnectionNetService
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.doneRun
import org.jetbrains.io.*
import java.util.concurrent.atomic.AtomicInteger

val LOG: Logger = Logger.getInstance(FastCgiService::class.java)

// todo send FCGI_ABORT_REQUEST if client channel disconnected
abstract class FastCgiService(project: Project) : SingleConnectionNetService(project) {
  private val requestIdCounter = AtomicInteger()
  protected val requests: ConcurrentIntObjectMap<Channel> = ContainerUtil.createConcurrentIntObjectMap<Channel>()

  override fun configureBootstrap(bootstrap: Bootstrap, errorOutputConsumer: Consumer<String>) {
    bootstrap.handler {
      it.pipeline().addLast("fastCgiDecoder", FastCgiDecoder(errorOutputConsumer, this@FastCgiService))
      it.pipeline().addLast("exceptionHandler", ChannelExceptionHandler.getInstance())

      it.closeFuture().addChannelListener {
        requestIdCounter.set(0)
        if (!requests.isEmpty) {
          val waitingClients = requests.elements().toList()
          requests.clear()
          for (channel in waitingClients) {
            sendBadGateway(channel)
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
      if (processHandler.has()) {
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
      else {
        promise = processHandler.get()
      }

      promise
        .doneRun { fastCgiRequest.writeToServerChannel(notEmptyContent, processChannel.get()!!) }
        .rejected {
          Promise.logError(LOG, it)
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
      val channel = requests.remove(fastCgiRequest.requestId)
      if (channel != null) {
        sendBadGateway(channel)
      }
    }
  }

  fun allocateRequestId(channel: Channel): Int {
    var requestId = requestIdCounter.getAndIncrement()
    if (requestId >= java.lang.Short.MAX_VALUE) {
      requestIdCounter.set(0)
      requestId = requestIdCounter.getAndDecrement()
    }
    requests.put(requestId, channel)
    return requestId
  }

  fun responseReceived(id: Int, buffer: ByteBuf?) {
    val channel = requests.remove(id)
    if (channel == null || !channel.isActive) {
      buffer?.release()
      return
    }

    if (buffer == null) {
      Responses.sendStatus(HttpResponseStatus.BAD_GATEWAY, channel)
      return
    }

    val httpResponse = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buffer)
    try {
      parseHeaders(httpResponse, buffer)
      Responses.addServer(httpResponse)
      if (!HttpUtil.isContentLengthSet(httpResponse)) {
        HttpUtil.setContentLength(httpResponse, buffer.readableBytes().toLong())
      }
    }
    catch (e: Throwable) {
      buffer.release()
      try {
        LOG.error(e)
      }
      finally {
        Responses.sendStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR, channel)
      }
      return
    }

    channel.writeAndFlush(httpResponse)
  }
}

private fun sendBadGateway(channel: Channel) {
  try {
    if (channel.isActive) {
      Responses.sendStatus(HttpResponseStatus.BAD_GATEWAY, channel)
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

    if (builder.length == 0) {
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
        response.setStatus(HttpResponseStatus.OK)
      }
      else {
        response.setStatus(HttpResponseStatus.valueOf(Integer.parseInt(value.substring(0, index))))
      }
    }
    else if (!(key.startsWith("http") || key.startsWith("HTTP"))) {
      response.headers().add(key, value)
    }
  }
}