// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ide

import com.intellij.testFramework.DisposeModulesRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.TemporaryDirectory
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpResponseStatus
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.builtInWebServer.TOKEN_HEADER_NAME
import org.jetbrains.builtInWebServer.acquireToken
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Rule
import org.junit.rules.Timeout
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.TimeUnit

internal abstract class BuiltInServerTestCase {
  companion object {
    @JvmField
    @ClassRule
    val projectRule = ProjectRule()

    @BeforeClass
    @JvmStatic
    fun runServer() {
      BuiltInServerManager.getInstance().waitForStart()
    }
  }

  protected val tempDirManager = TemporaryDirectory()
  protected val manager = TestManager(projectRule, tempDirManager)

  @Rule
  @JvmField
  val ruleChain = RuleChain(
    tempDirManager,
    Timeout(60, TimeUnit.SECONDS),
    manager,
    DisposeModulesRule(projectRule)
  )

  protected open val urlPathPrefix = ""

  protected fun doTest(urlSuffix: String? = null,
                       asSignedRequest: Boolean = true, origin: String? = null,
                       responseStatus: Int = 200,
                       additionalCheck: ((connection: HttpResponse<InputStream>) -> Unit)? = null) {
    val serviceUrl = "http://localhost:${BuiltInServerManager.getInstance().port}$urlPathPrefix"
    var url = serviceUrl
    if (urlSuffix != null) {
      url += urlSuffix
    }
    else if (manager.filePath != null) {
      url += "/${manager.filePath}"
    }

    val line = manager.annotation?.line ?: -1
    if (line != -1) {
      url += ":$line"
    }
    val column = manager.annotation?.column ?: -1
    if (column != -1) {
      url += ":$column"
    }

    val expectedStatus = HttpResponseStatus.valueOf(manager.annotation?.status ?: responseStatus)
    val response = testUrl(url, expectedStatus, asSignedRequest = asSignedRequest, origin = origin)
    response.body().use {
      check(serviceUrl, expectedStatus)
      additionalCheck?.invoke(response)
    }
  }

  protected open fun check(serviceUrl: String, expectedStatus: HttpResponseStatus) {
  }
}

internal fun testUrl(url: String, expectedStatus: HttpResponseStatus, asSignedRequest: Boolean, origin: String? = null): HttpResponse<InputStream> {
  val builder = HttpRequest.newBuilder(URI(url))
  origin?.let {
    builder.header(HttpHeaderNames.ORIGIN.toString(), it)
  }
  if (asSignedRequest) {
    builder.header(TOKEN_HEADER_NAME, acquireToken())
  }

  val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build()
  val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream())
  assertThat(HttpResponseStatus.valueOf(response.statusCode())).isEqualTo(expectedStatus)
  return response
}