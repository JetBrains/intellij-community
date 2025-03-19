// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.tree

import com.intellij.debugger.engine.evaluation.EvaluationContext
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.xdebugger.frame.XValueChildrenList
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface ExtraDebugNodesProvider {
  fun addExtraNodes(evaluationContext: EvaluationContext, children: XValueChildrenList) {}

  companion object {
    private val EP_NAME = ExtensionPointName<ExtraDebugNodesProvider>("com.intellij.debugger.extraDebugNodesProvider")
    @JvmStatic
    fun getProviders(): List<ExtraDebugNodesProvider> = EP_NAME.extensionList
  }
}
