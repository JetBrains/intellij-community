// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.tree.render

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.engine.FullValueEvaluatorProvider
import com.intellij.debugger.engine.JavaValue.JavaFullValueEvaluator
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.impl.DebuggerUtilsImpl
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl
import com.intellij.execution.filters.ExceptionFilter
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.xdebugger.frame.XFullValueEvaluator
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import com.sun.jdi.ObjectReference
import com.sun.jdi.StringReference

internal class StackTraceElementObjectRenderer : CompoundRendererProvider() {
  override fun getName(): String = "StackTraceElement"

  override fun getClassName(): String = "java.lang.StackTraceElement"

  override fun isEnabled(): Boolean = true

  override fun getFullValueEvaluatorProvider(): FullValueEvaluatorProvider {
    return object : FullValueEvaluatorProvider {
      override fun getFullValueEvaluator(evaluationContext: EvaluationContextImpl, valueDescriptor: ValueDescriptorImpl): XFullValueEvaluator? {
        val value = valueDescriptor.value as? ObjectReference ?: return null
        return object : JavaFullValueEvaluator(JavaDebuggerBundle.message("message.node.navigate"), evaluationContext) {
          override fun isShowValuePopup(): Boolean = false

          override fun evaluate(callback: XFullValueEvaluationCallback) {
            try {
              val res = DebuggerUtilsImpl.invokeObjectMethod(evaluationContext, value, "toString", "()Ljava/lang/String;", emptyList())
              if (res is StringReference) {
                callback.evaluated("")
                val line = res.value()
                ReadAction.run<Throwable> {
                  val project = valueDescriptor.project
                  val filter = ExceptionFilter(project, evaluationContext.debugProcess.session.searchScope)
                  val result = filter.applyFilter(line, line.length)
                  if (result != null) {
                    val info = result.getFirstHyperlinkInfo()
                    if (info != null) {
                      DebuggerUIUtil.invokeLater { info.navigate(project) }
                    }
                  }
                }
              }
            }
            catch (e: EvaluateException) {
              logger<StackTraceElementObjectRenderer>().info("Exception while getting stack info", e)
            }
          }
        }
      }
    }
  }
}
