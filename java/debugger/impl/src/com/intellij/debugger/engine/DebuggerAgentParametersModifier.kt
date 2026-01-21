// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.execution.configurations.ParametersList
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface DebuggerAgentParametersModifier {
  fun modify(parametersList: ParametersList)

  companion object {
    private val EP: ExtensionPointName<DebuggerAgentParametersModifier> =
      ExtensionPointName.create("com.intellij.debugger.agentParametersModifier")

    @JvmStatic
    fun getAgentModifiers(): List<DebuggerAgentParametersModifier> = EP.extensionList
  }
}