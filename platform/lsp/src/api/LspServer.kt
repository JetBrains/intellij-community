// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api

/**
 * Renamed to [LspClient]: in [LSP terminology](https://microsoft.github.io/language-server-protocol/) the IDE is the client, not the
 * server. Kept as a deprecated marker subtype of [LspClient] at its original FQN so that existing consumers and casts keep working;
 * every running instance still implements `LspServer`.
 */
@Deprecated(
  "Renamed to LspClient",
  ReplaceWith("LspClient", "com.intellij.platform.lsp.api.LspClient"),
)
interface LspServer : LspClient {
  /**
   * Binary-compatibility shim that restores the original `LspServer.descriptor: LspServerDescriptor` JVM getter
   * (`getDescriptor()`) for consumers compiled against the pre-rename API.
   *
   * Declared as a function, not a property, so it only re-exposes the JVM entry point; it intentionally does not
   * shadow [LspClient.descriptor] for Kotlin sources, so recompiled Kotlin code should use [descriptor] instead.
   *
   * The cast is safe for any consumer compiled against the old API, which could only start a client with an
   * [LspServerDescriptor]. It can fail only for new code that starts a client with a plain [LspClientDescriptor]
   * yet still calls this deprecated method.
   */
  @Suppress("DEPRECATION")
  fun getDescriptor(): LspServerDescriptor =
    descriptor as? LspServerDescriptor
    ?: error("LspServer.getDescriptor() requires an LspServerDescriptor, but this client was started with " +
             "${descriptor.javaClass.name}. Use LspClient.descriptor instead.")

  companion object {
    @Deprecated(
      "Uee LspClient.DEFAULT_REQUEST_TIMEOUT_MS",
      ReplaceWith("LspClient.DEFAULT_REQUEST_TIMEOUT_MS", "com.intellij.platform.lsp.api.LspClient"),
    )
    const val DEFAULT_REQUEST_TIMEOUT_MS: Int = LspClient.DEFAULT_REQUEST_TIMEOUT_MS
  }
}
