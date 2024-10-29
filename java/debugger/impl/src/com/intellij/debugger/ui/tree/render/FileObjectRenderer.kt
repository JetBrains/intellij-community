// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.tree.render

import com.intellij.debugger.JavaDebuggerBundle.message
import com.intellij.debugger.engine.FullValueEvaluatorProvider
import com.intellij.debugger.engine.JavaValue
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.impl.DebuggerUtilsImpl
import com.intellij.debugger.settings.NodeRendererSettings
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.util.registry.Registry.Companion.`is`
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.xdebugger.frame.XFullValueEvaluator
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import com.sun.jdi.BooleanValue
import com.sun.jdi.ObjectReference
import com.sun.jdi.StringReference

class FileObjectRenderer : CompoundRendererProvider() {
  override fun getName(): String = "File"

  override fun getChildrenRenderer(): ChildrenRenderer = NodeRendererSettings.createExpressionChildrenRenderer("listFiles()", null)

  override fun getClassName(): String = "java.io.File"

  override fun isEnabled(): Boolean = `is`("debugger.renderers.file")

  override fun getFullValueEvaluatorProvider(): FullValueEvaluatorProvider? {
    return object : FullValueEvaluatorProvider {
      override fun getFullValueEvaluator(evaluationContext: EvaluationContextImpl, valueDescriptor: ValueDescriptorImpl): XFullValueEvaluator? {
        val value = valueDescriptor.value as? ObjectReference ?: return null
        try {
          val isFile = DebuggerUtilsImpl.invokeObjectMethod(evaluationContext, value, "isFile", "()Z", emptyList())
          if ((isFile as? BooleanValue)?.value() == true) {
            return object : JavaValue.JavaFullValueEvaluator(message("message.node.open"), evaluationContext) {
              override fun isShowValuePopup(): Boolean = false

              override fun evaluate(callback: XFullValueEvaluationCallback) {
                val path = DebuggerUtilsImpl.invokeObjectMethod(evaluationContext, value, "getAbsolutePath", "()Ljava/lang/String;", emptyList())
                if (path is StringReference) {
                  callback.evaluated("")
                  val vFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(path.value()) ?: return
                  DebuggerUIUtil.invokeLater { PsiNavigationSupport.getInstance().createNavigatable(evaluationContext.project, vFile, -1).navigate(true) }
                }
              }
            }
          }
        }
        catch (_: EvaluateException) {
        }
        return null
      }
    }
  }
}
