// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.httpclient

import com.intellij.collaboration.api.HttpStatusErrorException
import java.awt.Image
import java.io.InputStream
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublisher
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.*
import java.nio.ByteBuffer
import java.util.concurrent.Flow
import javax.imageio.ImageIO

abstract class ByteArrayProducingBodyPublisher
  : BodyPublisher {

  override fun subscribe(subscriber: Flow.Subscriber<in ByteBuffer>) {
    val body = produceBytes()
    BodyPublishers.ofByteArray(body).subscribe(subscriber)
  }

  protected abstract fun produceBytes(): ByteArray

  override fun contentLength(): Long = -1
}

abstract class StreamReadingBodyHandler<T>(protected val request: HttpRequest) : BodyHandler<T> {

  final override fun apply(responseInfo: ResponseInfo): BodySubscriber<T> {
    val subscriber = HttpClientUtil.gzipInflatingBodySubscriber(responseInfo)
    return BodySubscribers.mapping(subscriber) {
      val statusCode = responseInfo.statusCode()
      if (statusCode >= 400) {
        val errorBody = it.reader().readText()
        handleError(statusCode, errorBody)
      }
      read(it)
    }
  }

  protected abstract fun read(bodyStream: InputStream): T

  protected open fun handleError(statusCode: Int, errorBody: String): Nothing {
    throw HttpStatusErrorException(request.method(), request.uri().toString(), statusCode, errorBody)
  }
}

open class ImageBodyHandler(request: HttpRequest) : StreamReadingBodyHandler<Image>(request) {
  override fun read(bodyStream: InputStream): Image = ImageIO.read(bodyStream)
}