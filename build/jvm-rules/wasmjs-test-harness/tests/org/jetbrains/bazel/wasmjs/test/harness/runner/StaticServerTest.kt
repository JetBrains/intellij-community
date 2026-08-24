// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.wasmjs.test.harness.runner

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class StaticServerTest {
  @Test
  fun `serves files with content types and the in-memory page as index html`() = withServer { base, _ ->
    assertEquals("<html>index</html>" to "text/html; charset=utf-8", get(base.resolve("index.html")))
    assertEquals("<html>index</html>" to "text/html; charset=utf-8", get(base))
    assertEquals("export {}" to "text/javascript", get(base.resolve("module-js/module.mjs")))
    assertEquals("data" to "text/plain; charset=utf-8", get(base.resolve("testdata/sample.txt")))
  }

  @Test
  fun `unknown files and directories are 404 and logged`() = withServer { base, log ->
    assertEquals(404, send(base.resolve("missing.txt")).statusCode())
    assertEquals(404, send(base.resolve("module-js/")).statusCode())
    assertTrue(log.toString().contains("static server 404: /missing.txt"))
    assertTrue(log.toString().contains("static server 404: /module-js/"))
  }

  @Test
  fun `path escapes are rejected`() = withServer { base, _ ->
    assertEquals(404, send(URI(base.toString() + "../outside.txt")).statusCode())
    assertEquals(404, send(URI(base.toString() + "testdata/%2e%2e/%2e%2e/outside.txt")).statusCode())
    assertEquals(404, send(URI(base.toString() + "%2e%2e%2foutside.txt")).statusCode())
  }

  @Test
  fun `a response failure is logged instead of being silently swallowed`(): Unit = runBlocking {
    val root = Files.createTempDirectory("static-server-error-test")
    val denied = root.resolve("denied.txt")
    denied.writeText("must fail to be served")
    assumeTrue(root.fileSystem.supportedFileAttributeViews().contains("posix"))
    Files.setPosixFilePermissions(denied, emptySet())
    assumeFalse(Files.isReadable(denied))

    val log = InfrastructureLog()
    withStaticServer(root, "<html>index</html>", log) { server ->
      val base = server.serve()
      assertEquals(500, send(base.resolve("denied.txt")).statusCode())
      assertTrue(log.toString().contains("static server error for /denied.txt"))
    }
  }

  private fun withServer(block: suspend (URI, InfrastructureLog) -> Unit): Unit = runBlocking {
    val root = Files.createTempDirectory("static-server-test")
    root.parent.resolve("outside.txt").writeText("must never be served")
    root.resolve("module-js").createDirectories()
    root.resolve("module-js/module.mjs").writeText("export {}")
    root.resolve("testdata").createDirectories()
    root.resolve("testdata/sample.txt").writeText("data")
    val log = InfrastructureLog()
    withStaticServer(root, "<html>index</html>", log) { server ->
      block(server.serve(), log)
    }
  }

  private fun get(uri: URI): Pair<String, String> {
    val response = send(uri)
    assertEquals(200, response.statusCode())
    return response.body() to response.headers().firstValue("Content-Type").orElse("").orEmpty()
  }

  private fun send(uri: URI): HttpResponse<String> =
    HttpClient.newHttpClient().send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString())
}
