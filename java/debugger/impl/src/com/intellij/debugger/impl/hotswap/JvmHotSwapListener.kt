// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl.hotswap

import com.intellij.debugger.impl.DebuggerSession
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

/**
 * Listener for JVM class redefinition performed by the debugger HotSwap flow.
 *
 * Class names passed to this listener use Java binary-name syntax: package components are separated with `.`, and nested
 * classes are separated from enclosing classes with `$`, for example `com.example.Outer$Inner`. They are not JVM internal
 * names with `/` separators.
 *
 * Implementations are invoked on the debugger manager thread.
 */
@ApiStatus.Internal
interface JvmHotSwapListener {
  /**
   * Called after modified classes are collected and immediately before the debugger starts redefining them in the target VM.
   *
   * @param session the debugger session that performs HotSwap
   * @param classesToReload fully-qualified class names selected for redefinition, using Java binary-name delimiters
   */
  fun beforeHotSwap(session: DebuggerSession, classesToReload: Set<String>) {}

  /**
   * Called after the HotSwap attempt finishes, even if redefinition failed or was cancelled.
   *
   * @param session the debugger session that performed HotSwap
   * @param classesToReload fully-qualified class names that were selected for redefinition before the attempt started, using Java
   *                        binary-name delimiters
   * @param reloadedClasses fully-qualified class names that were successfully redefined in the target VM, using Java binary-name
   *                        delimiters; this can be a subset of [classesToReload], or empty when no class was redefined
   */
  fun afterHotSwap(session: DebuggerSession, classesToReload: Set<String>, reloadedClasses: Set<String>) {}

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<JvmHotSwapListener> = ExtensionPointName.create("com.intellij.debugger.jvmHotSwapListener")
  }
}
