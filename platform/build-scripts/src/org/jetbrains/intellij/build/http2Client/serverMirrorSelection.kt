// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.http2Client

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import java.net.URI

internal suspend fun <T> checkMirrorAndConnect(
  initialServerUri: URI,
  client: Http2ClientConnectionFactory,
  authHeader: CharSequence? = null,
  block: suspend (connection: Http2ClientConnection, urlPathPrefix: String) -> T,
): T {
  return spanBuilder("mirror selection").setAttribute("initialServerUri", initialServerUri.toString()).use { span ->
    // first let's check for initial redirect (mirror selection)
    var connection = client.connect(address = initialServerUri, authHeader = authHeader)
    try {
      var urlPath = initialServerUri.path
      val newLocation = connection.getRedirectLocation("$urlPath/")?.toString()
      if (newLocation == null) {
        span.addEvent("origin server will be used", Attributes.of(AttributeKey.stringKey("url"), initialServerUri.toString()))
      }
      else {
        connection.close()

        val newServerUri = URI(newLocation)
        urlPath = newServerUri.path.trimEnd('/')
        span.addEvent("redirected to mirror", Attributes.of(AttributeKey.stringKey("url"), newLocation))
        connection = client.connect(address = newServerUri, authHeader = authHeader)
      }
      block(connection, urlPath)
    }
    finally {
      connection.close()
    }
  }
}