// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.openapi.externalSystem.util.wsl

import com.intellij.util.ExceptionUtil
import org.jetbrains.annotations.ApiStatus
import java.net.ConnectException

/**
 * Connects to a remote server with retrying mechanism.
 *
 * If the [action] causes [ConnectException] it will be retried after [step] milliseconds.
 * If all retries fail after [timeoutMillis] , the first [ConnectException] will be rethrown
 *
 * @param timeoutMillis The maximum timeout in milliseconds to wait for a successful connection.
 * @param step The interval in milliseconds between retries. Default value is 100 milliseconds.
 * @param action The action to connect to the remote server. Must not return null in case of success
 * @return The result of the action.
 * @throws ConnectException if unable to connect to the server within the specified timeout.
 * @throws java.lang.RuntimeException if an unexpected failure occurs while connecting to the remote server.
 */
fun <T> connectRetrying(timeoutMillis: Long, step: Long = 100, action: () -> T): T {
  val start = System.currentTimeMillis()
  var result: T?
  var lastException: Throwable? = null
  do {
    result = try {
      action()
    }
    catch (e: Throwable) {
      val rootCause = ExceptionUtil.getRootCause(e)
      if (rootCause is ConnectException) {
        lastException = e
        Thread.sleep(step)
        null
      } else {
        throw e
      }
    }
  } while (result == null && (System.currentTimeMillis() - start < timeoutMillis))

  if (result == null) {
    if (lastException != null) {
      throw lastException
    } else {
      throw RuntimeException("Unexpected failure while connecting to remote server")
    }
  }
  return result
}