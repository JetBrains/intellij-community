/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.ui.tree.render

import com.intellij.debugger.DebuggerBundle
import com.intellij.debugger.engine.FullValueEvaluatorProvider
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContext
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl
import com.intellij.debugger.ui.tree.ValueDescriptor
import com.intellij.openapi.util.Key
import com.intellij.xdebugger.frame.XFullValueEvaluator
import com.intellij.xdebugger.frame.XValuePlace
import com.intellij.xdebugger.impl.ui.tree.nodes.HeadlessValueEvaluationCallback

/**
 * @author egor
 */
class LazyToStringRenderer : ToStringRenderer(), FullValueEvaluatorProvider {
  init {
    isEnabled = true
  }

  override fun getFullValueEvaluator(evaluationContext: EvaluationContextImpl?, valueDescriptor: ValueDescriptorImpl): XFullValueEvaluator? {
    if (!isCalculated(valueDescriptor)) {
      return object : XFullValueEvaluator(DebuggerBundle.message("message.node.toString")) {
        override fun startEvaluation(callback: XFullValueEvaluator.XFullValueEvaluationCallback) {
          if (callback is HeadlessValueEvaluationCallback) {
            val node = callback.node
            node.clearFullValueEvaluator()
            valueDescriptor.putUserData(LAZY_CALCULATED, true)
            node.valueContainer.computePresentation(node, XValuePlace.TREE)
          }
          callback.evaluated("")
        }
      }.setShowValuePopup(false)
    }
    return null
  }

  @Throws(EvaluateException::class)
  override fun calcLabel(descriptor: ValueDescriptor, evaluationContext: EvaluationContext, listener: DescriptorLabelListener): String? {
    if (!isCalculated(descriptor)) {
      return ""
    }
    return super.calcLabel(descriptor, evaluationContext, listener)
  }

  fun isCalculated(descriptor: ValueDescriptor): Boolean {
    return LAZY_CALCULATED.get(descriptor, false)
  }

  override fun getName(): String {
    return "Lazy" + super.getName()
  }

  override fun getUniqueId(): String {
    return "Lazy" + super.getUniqueId()
  }

  companion object {
    @JvmField
    val LAZY_CALCULATED = Key.create<Boolean>("LAZY_CALCULATED")
  }
}
