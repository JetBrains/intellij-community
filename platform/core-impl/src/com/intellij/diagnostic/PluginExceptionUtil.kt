// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.openapi.project.IndexNotReadyException
import org.jetbrains.annotations.ApiStatus
import java.util.function.Supplier

@ApiStatus.Internal
object PluginExceptionUtil {
  private val LOG = logger<PluginExceptionUtil>()

  /**
   * Executes `code` that calls third-party plugin code and converts any exception it throws into a [PluginException] attributed
   * to `pluginClass`, so the failure is reported against the responsible plugin instead of the platform.
   *
   * When `message` is not `null`, it is used as the error message of the produced [PluginException].
   *
   * Control-flow exceptions and [IndexNotReadyException] are rethrown unchanged.
   */
  fun <T> computeWithPluginExceptions(pluginClass: Class<*>, message: String? = null, code: Supplier<out T>): Result<T> {
    try {
      return Result.success(code.get())
    }
    catch (e: IndexNotReadyException) {
      throw e
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      val pluginException = if (message == null) PluginException.createByClass(e, pluginClass)
                            else PluginException.createByClass(message, e, pluginClass)
      return Result.failure(pluginException)
    }
  }

  /**
   * Executes `code` that calls third-party plugin code and converts any exception it throws into a [PluginException] attributed to
   * `pluginClass`, so the failure is reported against the responsible plugin instead of the platform.
   *
   * When `message` is not `null`, it is used as the error message of the logged [PluginException].
   *
   * @return result of `code` execution or null if an error happens during its execution
   *
   * Control-flow exceptions and [IndexNotReadyException] are rethrown unchanged.
   */
  @JvmStatic
  @JvmOverloads
  fun <T : Any> computeOrLogPluginException(pluginClass: Class<*>, message: String? = null, code: Supplier<out T>): T? {
    return computeWithPluginExceptions(pluginClass, message, code)
      .onFailure { LOG.error(it) }
      .getOrNull()
  }
}
