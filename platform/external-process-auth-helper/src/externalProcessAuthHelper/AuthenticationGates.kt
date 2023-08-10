// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.externalProcessAuthHelper

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.function.Supplier

private val LOG = logger<RestrictingAuthenticationGate>()

class RestrictingAuthenticationGate : AuthenticationGate {
  private val semaphore = Semaphore(1)
  @Volatile private var cancelled = false
  private val inputData = ConcurrentHashMap<String, String>()

  override fun <T> waitAndCompute(operation: Supplier<T>): T {
    try {
      LOG.debug("Entered waitAndCompute")
      semaphore.acquire()
      LOG.debug("Acquired permission")
      if (cancelled) {
        LOG.debug("Authentication Gate has already been cancelled")
        throw ProcessCanceledException()
      }
      return operation.get()
    }
    catch (e: InterruptedException) {
      LOG.warn(e)
      throw ProcessCanceledException(e)
    }
    finally {
      semaphore.release()
    }
  }

  override fun cancel() {
    cancelled = true
  }

  override fun getSavedInput(key: String): String? {
    return inputData[key]
  }

  override fun saveInput(key: String, value: String) {
    inputData[key] = value
  }
}

class PassthroughAuthenticationGate : AuthenticationGate {
  override fun <T> waitAndCompute(operation: Supplier<T>): T {
    return operation.get()
  }

  override fun cancel() {
  }

  override fun getSavedInput(key: String): String? {
    return null
  }

  override fun saveInput(key: String, value: String) {
  }

  companion object {
    @JvmStatic val instance = PassthroughAuthenticationGate()
  }
}
