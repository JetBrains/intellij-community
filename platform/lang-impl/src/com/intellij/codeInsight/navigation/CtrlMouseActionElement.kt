// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation

import com.intellij.concurrency.IntelliJContextElement
import com.intellij.concurrency.currentThreadContext
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.coroutines.CoroutineContext

/**
 * The action Ctrl+hover is computing [CtrlMouseData] for.
 *
 * Ctrl+hover asks a [CtrlMouseAction] where the declaration under the mouse pointer is without performing the action, so
 * [com.intellij.openapi.actionSystem.ex.ActionContextElement] is not installed and `AnActionListener` doesn't fire.
 * Providers that resolve references only for a running navigation action -- because resolving costs a request to an external
 * tool, for example -- can check [current] to answer during Ctrl+hover too.
 *
 * This follows the computation rather than the thread: Ctrl+hover cancels and restarts on every mouse move, so a cancelled
 * computation is usually still unwinding on another thread while its successor runs.
 */
@Internal
class CtrlMouseActionElement(val actionId: String) : CoroutineContext.Element, IntelliJContextElement {

  override val key: CoroutineContext.Key<*> = CtrlMouseActionElement

  override fun produceChildElement(parentContext: CoroutineContext, isStructured: Boolean): IntelliJContextElement = this

  override fun toString(): String = "CtrlMouseActionElement($actionId)"

  companion object : CoroutineContext.Key<CtrlMouseActionElement> {

    @RequiresBlockingContext
    @JvmStatic
    fun current(): CtrlMouseActionElement? = currentThreadContext()[CtrlMouseActionElement]
  }
}
