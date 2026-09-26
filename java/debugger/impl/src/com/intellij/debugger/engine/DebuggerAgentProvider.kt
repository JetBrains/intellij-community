// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
interface DebuggerAgentProvider {
  fun getAgentArtifactPath(project: Project?, disposable: Disposable?): Path?

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<DebuggerAgentProvider> =
      ExtensionPointName.create("com.intellij.debugger.agentProvider")

    @JvmStatic
    fun getInstance(): DebuggerAgentProvider? {
      val providers = EP_NAME.extensionList
      check(providers.size <= 1) { "Only one debugger agent provider is allowed" }
      return providers.firstOrNull()
    }
  }
}
