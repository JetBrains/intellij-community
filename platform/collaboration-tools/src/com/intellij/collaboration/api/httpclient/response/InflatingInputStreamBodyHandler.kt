// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.httpclient.response

import com.intellij.collaboration.api.httpclient.HttpClientUtil
import java.io.InputStream
import java.net.http.HttpResponse.*
import java.util.zip.GZIPInputStream

internal class InflatingInputStreamBodyHandler : BodyHandler<InputStream> {

  override fun apply(responseInfo: ResponseInfo): BodySubscriber<InputStream> {
    val inputStreamSubscriber = BodySubscribers.ofInputStream()
    return if (responseInfo.headers().allValues(HttpClientUtil.CONTENT_ENCODING_HEADER).contains(HttpClientUtil.CONTENT_ENCODING_GZIP)) {
      BodySubscribers.mapping(inputStreamSubscriber) {
        GZIPInputStream(it)
      }
    }
    else {
      inputStreamSubscriber
    }
  }
}