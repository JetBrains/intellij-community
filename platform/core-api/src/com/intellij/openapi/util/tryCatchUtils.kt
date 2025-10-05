// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util

import com.intellij.openapi.diagnostic.ControlFlowException
import kotlinx.coroutines.CancellationException
import org.jetbrains.annotations.ApiStatus


/**
 * Wraps the block into try-catch with a rethrow of CancellationException and ControlFlowException.
 */
@ApiStatus.Internal
fun <T> (() -> T).withSafeCatch(catch: (Throwable) -> T?): T? =
  try {
    this()
  }
  catch (t: Throwable) {
    throwIfNecessary(t)
    catch(t)
  }

/**
 * Wraps the suspendable block into try-catch with a rethrow of CancellationException and ControlFlowException.
 */
@ApiStatus.Internal
suspend fun <T> (suspend () -> T).withSafeCatch(catch: suspend (Throwable) -> T?): T? =
  try {
    this()
  }
  catch (t: Throwable) {
    throwIfNecessary(t)
    catch(t)
  }

/**
 * Wraps the block into try-catch-finally with a rethrow of CancellationException and ControlFlowException.
 */
@ApiStatus.Internal
fun <T> (() -> T).withSafeCatchFinally(catch: (Throwable) -> T?, finally: () -> Unit): T? =
  try {
    this()
  }
  catch (t: Throwable) {
    throwIfNecessary(t)
    catch(t)
  }
  finally {
    finally()
  }

/**
 * Wraps the suspendable block into try-catch-finally with a rethrow of CancellationException and ControlFlowException.
 */
@ApiStatus.Internal
suspend fun <T> (() -> T).withSafeCatchFinally(catch: suspend (Throwable) -> T?, finally: suspend () -> Unit): T? =
  try {
    this()
  }
  catch (t: Throwable) {
    throwIfNecessary(t)
    catch(t)
  }
  finally {
    finally()
  }

private fun throwIfNecessary(t: Throwable) {
  if (t is ControlFlowException || t is CancellationException) throw t
}
