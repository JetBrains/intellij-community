// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.compilation

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import org.jetbrains.intellij.build.http2Client.Http2ClientConnection
import org.jetbrains.intellij.build.http2Client.Http2ClientConnectionFactory
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import java.net.URI

internal suspend fun <T> checkMirrorAndConnect(
  initialServerUri: URI,
  client: Http2ClientConnectionFactory,
  authHeader: CharSequence? = null,
  block: suspend (connection: Http2ClientConnection, urlPathPrefix: String) -> T,
): T {
  var urlPath = initialServerUri.path
  // first let's check for initial redirect (mirror selection)
  var connection = client.connect(host = initialServerUri.host, port = initialServerUri.port, authHeader = authHeader)
  try {
    spanBuilder("mirror selection").use { span ->
      val newLocation = connection.getRedirectLocation("$urlPath/")?.toString()
      if (newLocation == null) {
        span.addEvent("origin server will be used", Attributes.of(AttributeKey.stringKey("url"), initialServerUri.toString()))
      }
      else {
        connection.close()

        val newServerUri = URI(newLocation)
        urlPath = newServerUri.path.trimEnd('/')
        span.addEvent("redirected to mirror", Attributes.of(AttributeKey.stringKey("url"), newLocation))
        connection = client.connect(host = newServerUri.host, port = newServerUri.port, authHeader = authHeader)
      }
    }
    return block(connection, urlPath)
  }
  finally {
    connection.close()
  }
}