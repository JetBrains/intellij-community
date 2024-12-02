// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarRepository

import com.intellij.jarRepository.JarRepositoryAuthenticationDataProvider.AuthenticationData
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.ResponseSent
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.authorization
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import java.util.*

object JarHttpDownloaderTestUtil {
  private const val LOCALHOST = "127.0.0.1"

  class TestHttpServerExtension(private val init: (ApplicationEngine) -> Unit = {}) : BeforeEachCallback, AfterEachCallback {
    lateinit var server: ApplicationEngine
    private val logBuffer: StringBuffer = StringBuffer()
    val log: String
      get() = logBuffer.toString()

    override fun beforeEach(context: ExtensionContext?) {
      server = embeddedServer(CIO, host = LOCALHOST, port = 0) {
        install(createApplicationPlugin(name = "Log calls") {
          on(ResponseSent) { call ->
            logBuffer.appendLine("${call.request.uri}: ${call.response.status()?.value}")
          }
        })
      }.start(wait = false)
      init(server)
    }

    override fun afterEach(context: ExtensionContext?) {
      server.stop(0)
    }
  }

  fun ApplicationEngine.createContext(
    path: String,
    httpStatusCode: HttpStatusCode,
    log: (String) -> Unit = {},
    response: String? = null,
    auth: AuthenticationData? = null,
    delayMs: Long = 0,
  ) {
    application.routing {
      get(path) {
        delay(delayMs)

        if (auth != null) {
          val authString = Base64.getEncoder().encodeToString((auth.userName + ":" + auth.password).toByteArray())

          val authHeader = call.request.authorization()
          if (authHeader == null) {
            log("$path: missing auth")
            call.respond(HttpStatusCode.Unauthorized)
            return@get
          }

          if (authHeader != "Basic $authString") {
            log("$path: wrong auth")
            call.respond(HttpStatusCode.Unauthorized)
            return@get
          }
        }

        log("$path: ${httpStatusCode.value}")

        val bytes = response?.toByteArray() ?: byteArrayOf()
        call.respond(httpStatusCode, bytes)
      }
    }
  }

  val ApplicationEngine.url: String
    get() = runBlocking {
      "http://${resolvedConnectors().first().host}:${resolvedConnectors().first().port}"
    }
}