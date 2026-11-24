// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.impl

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.thisLogger
import org.eclipse.lsp4j.ProgressParams
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * The `$/progress` consumers of the requests of one [LspClientImpl] that run with a `partialResultToken`,
 * see [com.intellij.platform.lsp.api.LspClient.sendRequestWithPartialResults].
 *
 * Every function is safe to call from any thread.
 */
internal class LspPartialResults {
  private val consumers = ConcurrentHashMap<String, (ProgressParams) -> Unit>()
  private val lastTokenNumber = AtomicLong()

  /**
   * A token that no other request of this client uses. The prefix separates it from a token that the server makes for
   * its own work-done progress, so a server token never hits a consumer of this registry.
   */
  fun newToken(): String = "ij-partial-result-${lastTokenNumber.incrementAndGet()}"

  fun register(token: String, consumer: (ProgressParams) -> Unit) {
    consumers[token] = consumer
  }

  fun unregister(token: String) {
    consumers.remove(token)
  }

  /**
   * Drops every consumer. The client calls it when the server stops: a request that never gets an answer never reaches
   * its own [unregister], and its consumer would stay here for the lifetime of the client.
   */
  fun clear() {
    consumers.clear()
  }

  /**
   * Gives [params] to the consumer of its token and returns `true`. Returns `false` when no request waits for that token,
   * and the caller then handles [params] as work-done progress.
   *
   * A consumer that fails gets its exception logged instead. An exception on the thread that reads the messages of the
   * server stops that thread and tears the server down.
   */
  fun dispatch(params: ProgressParams): Boolean {
    val token = params.token?.map({ it }, { it.toString() }) ?: return false
    val consumer = consumers[token] ?: return false
    try {
      consumer(params)
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      thisLogger().error("A partial result consumer of the token $token failed", e)
    }
    return true
  }
}
