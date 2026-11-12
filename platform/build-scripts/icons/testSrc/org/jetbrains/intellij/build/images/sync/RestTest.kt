package org.jetbrains.intellij.build.images.sync

import org.jetbrains.intellij.build.io.HttpTestServer
import org.jetbrains.intellij.build.io.respond
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.parallel.ResourceLock
import org.junit.jupiter.api.parallel.Resources
import java.net.http.HttpRequest
import java.util.Base64
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicInteger

@Timeout(20)
class RestTest {
  @Test
  @ResourceLock(Resources.SYSTEM_PROPERTIES)
  fun `TeamCity requests use HTTP 1 and Basic authentication`() {
    val userName = "us\u00e9r"
    val password = "p\u00e4ss"
    val previousUserName = System.setProperty("pin.builds.user.name", userName)
    val previousPassword = System.setProperty("pin.builds.user.password", password)
    try {
      val expectedAuth = "Basic " + Base64.getEncoder().encodeToString("$userName:$password".toByteArray(Charsets.ISO_8859_1))
      HttpTestServer { exchange ->
        assertEquals("HTTP/1.1", exchange.protocol)
        assertEquals(expectedAuth, exchange.requestHeaders.getFirst("Authorization"))
        assertEquals("application/xml; charset=utf-8", exchange.requestHeaders.getFirst("Content-Type"))
        assertEquals("<build/>", exchange.requestBody.readAllBytes().toString(Charsets.UTF_8))
        exchange.respond("created")
      }.use { server ->
        assertEquals("created", post(server.uri.toString(), "<build/>", "application/xml") { teamCityAuth() })
      }
    }
    finally {
      if (previousUserName == null) System.clearProperty("pin.builds.user.name")
      else System.setProperty("pin.builds.user.name", previousUserName)
      if (previousPassword == null) System.clearProperty("pin.builds.user.password")
      else System.setProperty("pin.builds.user.password", previousPassword)
    }
  }

  @Test
  fun `a webhook preserves its body and optional content type`() {
    HttpTestServer { exchange ->
      assertEquals("POST", exchange.requestMethod)
      assertNull(exchange.requestHeaders.getFirst("Content-Type"))
      assertEquals("{\"text\":\"test\"}", exchange.requestBody.readAllBytes().toString(Charsets.UTF_8))
      exchange.respond("ok")
    }.use { server ->
      assertEquals("ok", post(server.uri.toString(), "{\"text\":\"test\"}", mediaType = null))
    }
  }

  @Test
  fun `GET returns the response text`() {
    HttpTestServer { exchange ->
      assertEquals("GET", exchange.requestMethod)
      exchange.respond("response")
    }.use { server ->
      assertEquals("response", rest(HttpRequest.newBuilder(server.uri).build()))
    }
  }

  @Test
  fun `a failed POST reports the status and body without retrying`() {
    val requests = AtomicInteger()
    HttpTestServer { exchange ->
      requests.incrementAndGet()
      exchange.requestBody.readAllBytes()
      exchange.respond("unavailable", 503)
    }.use { server ->
      val failure = assertThrows<IllegalStateException> { post(server.uri.toString(), "request", mediaType = null) }
      assertTrue(failure.message.orEmpty().contains("503 unavailable"))
    }
    assertEquals(1, requests.get())
  }

  @Test
  fun `best effort calls propagate caller interruption`() {
    val interrupted = InterruptedException("The caller stopped")
    assertSame(interrupted, assertThrows<InterruptedException> { callSafely { throw interrupted } })
  }

  @Test
  fun `best effort calls propagate scope cancellation`() {
    val cancelled = CancellationException("The owner stopped")
    assertSame(cancelled, assertThrows<CancellationException> { callSafely { throw cancelled } })
  }
}
