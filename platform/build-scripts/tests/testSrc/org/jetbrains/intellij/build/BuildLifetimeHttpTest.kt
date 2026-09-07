package org.jetbrains.intellij.build

import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpRequest
import java.util.concurrent.Executors

@Timeout(20)
class BuildLifetimeHttpTest {
  @Test
  fun `closing a build closes its owned session`() {
    withServer { uri ->
      val lifetime = BuildLifetime()
      val session = lifetime.http
      lifetime.use {
        assertThat(downloadAsText(uri.toString(), session)).isEqualTo("response")
      }
      assertThrows<IllegalStateException> { lifetime.http }
      assertThrows<IllegalStateException> { session.send(HttpRequest.newBuilder(uri).GET().build()) }
    }
  }

  @Test
  fun `closing a build preserves a borrowed session`() {
    withServer { uri ->
      BuildHttpSession().use { session ->
        BuildLifetime(session).use { sibling ->
          BuildLifetime(session).use { first ->
            assertThat(first.http).isSameAs(session)
            assertThat(downloadAsText(uri.toString(), first.http)).isEqualTo("response")
          }
          assertThat(sibling.http).isSameAs(session)
          assertThat(downloadAsText(uri.toString(), sibling.http)).isEqualTo("response")
        }
        assertThat(downloadAsText(uri.toString(), session)).isEqualTo("response")
      }
    }
  }

  @Test
  fun `closing an independent build preserves another build`() {
    withServer { uri ->
      BuildLifetime().use { sibling ->
        BuildLifetime().use { first ->
          assertThat(first.http).isNotSameAs(sibling.http)
          downloadAsText(uri.toString(), first.http)
          downloadAsText(uri.toString(), sibling.http)
        }
        assertThat(downloadAsText(uri.toString(), sibling.http)).isEqualTo("response")
      }
    }
  }

  private fun withServer(action: (URI) -> Unit) {
    Executors.newVirtualThreadPerTaskExecutor().use { executor ->
      val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
      server.executor = executor
      server.createContext("/") { exchange ->
        exchange.use {
          val body = "response".toByteArray()
          it.sendResponseHeaders(200, body.size.toLong())
          it.responseBody.write(body)
        }
      }
      server.start()
      try {
        action(URI("http://127.0.0.1:${server.address.port}"))
      }
      finally {
        server.stop(0)
      }
    }
  }
}
