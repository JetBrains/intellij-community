// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.jcef.test.rhtest;

import com.intellij.internal.jcef.test.cases.DetailedFrame
import com.intellij.openapi.diagnostic.thisLogger
import org.cef.callback.CefCallback
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefResourceHandler
import org.cef.misc.IntRef
import org.cef.misc.StringRef
import org.cef.network.CefRequest
import org.cef.network.CefResponse
import java.io.IOException
import java.io.InputStream

internal class MyResourceHandler : CefResourceHandler {

    private var inputStream: InputStream? = null
    private var resourceType: CefRequest.ResourceType? = null

  private fun loadContent(resName: String?): InputStream? {
      try {
        return DetailedFrame::class.java.getResourceAsStream("resources/rhtest/" + resName)
      }
      catch (e: IOException) {
      }

    return null
  }

    override fun processRequest(
      request: CefRequest, callback: CefCallback,
    ): Boolean {
        thisLogger().info("Processing request ${request.url}")

        inputStream = loadContent("index.html")

        if (inputStream == null) {
            thisLogger().info("inputStream is null for ${request.url} , canceling request ${request.url}")
            callback.cancel()
            return false
        }

        resourceType = request.resourceType
        callback.Continue()
        return true
    }

    override fun getResponseHeaders(
      response: CefResponse, responseLength: IntRef, redirectUrl: StringRef,
    ) {
        if (inputStream == null) {
            response.error = CefLoadHandler.ErrorCode.ERR_FILE_NOT_FOUND
            response.statusText = "file not found"
            response.status = 404
            return
        }

        response.status = 200
        response.mimeType = getMimeType()
        try {
            responseLength.set(inputStream!!.available())
        } catch (e: IOException) {
            response.error = CefLoadHandler.ErrorCode.ERR_ABORTED
            response.statusText = "internal error"
            response.status = 500
        }
    }

    override fun readResponse(
      dataOut: ByteArray, bytesToRead: Int, bytesRead: IntRef, callback: CefCallback,
    ): Boolean {
        return try {

            val read = inputStream?.read(dataOut, 0, bytesToRead)
            if (read == null || read <= 0) {
                bytesRead.set(0)
                inputStream?.close()
                return false
            }
            bytesRead.set(read)
            true
        } catch (e: Exception) {
            bytesRead.set(0)
            thisLogger().error(e)
            false
        }
    }


    private fun getMimeType(): String {
        return when (resourceType) {
            CefRequest.ResourceType.RT_MAIN_FRAME -> {
                "text/html"
            }

            CefRequest.ResourceType.RT_SCRIPT -> {
                "text/javascript"
            }

            CefRequest.ResourceType.RT_STYLESHEET -> {
                "text/css"
            }

            CefRequest.ResourceType.RT_IMAGE -> {
//                if (path.endsWith("svg", true)) {
//                    "image/svg+xml"
//                } else {
                    "image/png"
//                }
            }

            else -> {
                "text/plain"
            }
        }
    }

    override fun cancel() {

    }

}
