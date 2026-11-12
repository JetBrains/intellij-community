// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ide

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpResponseStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.builtInWebServer.BuiltInWebServerAuth
import org.jetbrains.builtInWebServer.TOKEN_HEADER_NAME
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path

@Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
abstract class BuiltInServerTestCase {
  companion object {
    @JvmStatic
    protected val projectFixture: TestFixture<Project> = projectFixture(openAfterCreation = true)

    @BeforeAll
    @JvmStatic
    fun runServer() {
      BuiltInServerManager.getInstance().waitForStart()
    }
  }

  private val tempDirFixture: TestFixture<Path> = tempPathFixture()
  private val testManagerFixture: TestFixture<TestManager> = testManagerFixture(projectFixture, tempDirFixture)

  protected val project: Project
    get() = projectFixture.get()

  protected val tempDir: Path
    get() = tempDirFixture.get()

  protected val manager: TestManager
    get() = testManagerFixture.get()

  protected open val urlPathPrefix = ""

  @AfterEach
  fun disposeProjectModules() {
    val moduleManager = project.serviceIfCreated<ModuleManager>() ?: return
    timeoutRunBlocking {
      if (readAction { moduleManager.modules.isNotEmpty() }) {
        edtWriteAction {
          for (module in moduleManager.modules) {
            if (!module.isDisposed) {
              moduleManager.disposeModule(module)
            }
          }
        }
      }
    }
  }

  protected fun closeAllFiles() {
    if (project.serviceIfCreated<FileEditorManager>() == null) {
      return
    }
    timeoutRunBlocking {
      withContext(Dispatchers.EDT) {
        EditorTestUtil.closeAllFilesAndClearEditorHistory(project)
      }
    }
  }

  protected fun doTest(
    urlSuffix: String? = null,
    asSignedRequest: Boolean = true,
    origin: String? = null,
    responseStatus: Int = 200,
    additionalCheck: ((connection: HttpResponse<InputStream>) -> Unit)? = null,
  ) {
    val serviceUrl = "http://localhost:${BuiltInServerManager.getInstance().port}${urlPathPrefix}"
    var url = serviceUrl
    if (urlSuffix != null) {
      url += urlSuffix
    }
    else if (manager.filePath != null) {
      url += "/${manager.filePath}"
    }

    val line = manager.annotation?.line ?: -1
    if (line != -1) {
      url += ":${line}"
    }
    val column = manager.annotation?.column ?: -1
    if (column != -1) {
      url += ":${column}"
    }

    val expectedStatus = HttpResponseStatus.valueOf(manager.annotation?.status ?: responseStatus)
    val response = testUrl(url, expectedStatus, asSignedRequest = asSignedRequest, origin = origin)
    response.body().use {
      try {
        check(serviceUrl, expectedStatus)
        additionalCheck?.invoke(response)
      }
      finally {
        closeAllFiles()
      }
    }
  }

  protected open fun check(serviceUrl: String, expectedStatus: HttpResponseStatus) {}
}

internal fun testUrl(
  url: String,
  expectedStatus: HttpResponseStatus,
  asSignedRequest: Boolean,
  origin: String? = null,
): HttpResponse<InputStream> {
  val builder = HttpRequest.newBuilder(URI(url))
  origin?.let {
    builder.header(HttpHeaderNames.ORIGIN.toString(), it)
  }
  if (asSignedRequest) {
    builder.header(TOKEN_HEADER_NAME, service<BuiltInWebServerAuth>().acquireToken())
  }

  val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build()
  val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream())
  assertThat(HttpResponseStatus.valueOf(response.statusCode())).isEqualTo(expectedStatus)
  return response
}
