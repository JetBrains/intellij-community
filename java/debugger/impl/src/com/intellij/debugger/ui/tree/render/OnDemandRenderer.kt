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
import com.intellij.debugger.DebuggerContext
import com.intellij.debugger.engine.FullValueEvaluatorProvider
import com.intellij.debugger.engine.JavaValue
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContext
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl
import com.intellij.debugger.ui.tree.DebuggerTreeNode
import com.intellij.debugger.ui.tree.NodeDescriptor
import com.intellij.debugger.ui.tree.ValueDescriptor
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.xdebugger.frame.XFullValueEvaluator
import com.intellij.xdebugger.frame.XValuePlace
import com.intellij.xdebugger.impl.ui.tree.nodes.HeadlessValueEvaluationCallback
import com.sun.jdi.Type
import com.sun.jdi.Value
import javax.swing.Icon

/**
 * @author egor
 */
class OnDemandRenderer(val renderer: NodeRendererImpl) : NodeRendererImpl(), FullValueEvaluatorProvider {
  init {
    isEnabled = true
  }

  @Throws(EvaluateException::class)
  override fun calcLabel(descriptor: ValueDescriptor, evaluationContext: EvaluationContext, listener: DescriptorLabelListener): String? {
    if (!isCalculated(descriptor)) {
      return ""
    }
    return renderer.calcLabel(descriptor, evaluationContext, listener)
  }

  override fun getFullValueEvaluator(evaluationContext: EvaluationContextImpl?, valueDescriptor: ValueDescriptorImpl): XFullValueEvaluator? {
    if (!isCalculated(valueDescriptor)) {
      return createFullValueEvaluator(DebuggerBundle.message("message.node.toString"))
    }
    return null
  }

  override fun buildChildren(value: Value?, builder: ChildrenBuilder?, evaluationContext: EvaluationContext?) {
    renderer.buildChildren(value, builder, evaluationContext)
  }

  override fun getChildValueExpression(node: DebuggerTreeNode?, context: DebuggerContext?): PsiElement {
    return renderer.getChildValueExpression(node, context)
  }

  override fun isExpandable(value: Value?, evaluationContext: EvaluationContext?, parentDescriptor: NodeDescriptor?): Boolean {
    return renderer.isExpandable(value, evaluationContext, parentDescriptor)
  }

  override fun calcValueIcon(descriptor: ValueDescriptor?,
                             evaluationContext: EvaluationContext?,
                             listener: DescriptorLabelListener?): Icon? {
    return renderer.calcValueIcon(descriptor, evaluationContext, listener)
  }

  override fun isApplicable(type: Type?): Boolean {
    return renderer.isApplicable(type)
  }

  override fun getName(): String {
    return "OnDemand" + renderer.getName()
  }

  override fun getUniqueId(): String {
    return "OnDemand" + renderer.getUniqueId()
  }

  companion object {
    @JvmStatic
    fun createFullValueEvaluator(text: String): XFullValueEvaluator {
      return object : XFullValueEvaluator(text) {
        override fun startEvaluation(callback: XFullValueEvaluator.XFullValueEvaluationCallback) {
          if (callback is HeadlessValueEvaluationCallback) {
            val node = callback.node
            node.clearFullValueEvaluator()
            setCalculated((node.valueContainer as JavaValue).descriptor)
            node.valueContainer.computePresentation(node, XValuePlace.TREE)
          }
          callback.evaluated("")
        }
      }.setShowValuePopup(false)
    }

    @JvmField
    val ON_DEMAND_CALCULATED = Key.create<Boolean>("ON_DEMAND_CALCULATED")

    @JvmStatic
    fun isCalculated(descriptor: ValueDescriptor): Boolean {
      return ON_DEMAND_CALCULATED.get(descriptor, false)
    }

    @JvmStatic
    fun setCalculated(descriptor: ValueDescriptor) {
      ON_DEMAND_CALCULATED.set(descriptor, true)
    }
  }
}