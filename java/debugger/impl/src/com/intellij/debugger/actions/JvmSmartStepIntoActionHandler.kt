// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.actions.JvmSmartStepIntoActionHandler.JvmSmartStepIntoVariant
import com.intellij.debugger.engine.MethodFilter
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.TextRange
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.frame.XSuspendContext
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.stepping.ForceSmartStepIntoSource
import com.intellij.xdebugger.stepping.XSmartStepIntoHandler
import com.intellij.xdebugger.stepping.XSmartStepIntoVariant
import kotlinx.coroutines.future.future
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.asPromise
import org.jetbrains.concurrency.await
import org.jetbrains.concurrency.rejectedPromise
import javax.swing.Icon

internal class JvmSmartStepIntoActionHandler(private val session: DebuggerSession) : XSmartStepIntoHandler<JvmSmartStepIntoVariant>() {
  override fun computeSmartStepVariantsAsync(position: XSourcePosition): Promise<List<JvmSmartStepIntoVariant>> {
    return findVariants(position, true)
  }

  override fun computeStepIntoVariants(position: XSourcePosition): Promise<List<JvmSmartStepIntoVariant>> {
    return findVariants(position, false)
  }

  private fun findVariants(xPosition: XSourcePosition, smart: Boolean): Promise<List<JvmSmartStepIntoVariant>> {
    val scope = (session.xDebugSession as XDebugSessionImpl).getSuspendContextModel()?.coroutineScope ?: return rejectedPromise()
    val position = DebuggerUtilsEx.toSourcePosition(xPosition, session.project)
    val handler = JvmSmartStepIntoHandler.EP_NAME.findFirstSafe { it.isAvailable(position) }
                  ?: return rejectedPromise()
    return scope.future {
      val targetsPromise = if (smart) handler.findSmartStepTargetsAsync(position, session) else handler.findStepIntoTargets(position, session)
      val targets = targetsPromise.await()
      readAction { targets.map { JvmSmartStepIntoVariant(it, handler) } }
    }.asPromise()
  }

  override fun computeSmartStepVariants(position: XSourcePosition): List<JvmSmartStepIntoVariant> {
    throw IllegalStateException("Should not be called")
  }

  override fun getPopupTitle(): String = JavaDebuggerBundle.message("title.smart.step.popup")

  override fun stepIntoEmpty(session: XDebugSession) {
    session.forceStepInto()
  }

  override fun startStepInto(variant: JvmSmartStepIntoVariant, context: XSuspendContext?) {
    session.stepInto(true, variant.methodFilter)
  }

  internal class JvmSmartStepIntoVariant(
    private val target: SmartStepTarget,
    handler: JvmSmartStepIntoHandler,
  ) : XSmartStepIntoVariant(), ForceSmartStepIntoSource {
    val methodFilter: MethodFilter? = handler.createMethodFilter(target)

    override fun getText(): String = target.presentation

    override fun getIcon(): Icon? = target.icon

    override fun getHighlightRange(): TextRange? = target.highlightElement?.getTextRange()

    override fun needForceSmartStepInto(): Boolean = target is ForceSmartStepIntoSource && target.needForceSmartStepInto()
  }
}
