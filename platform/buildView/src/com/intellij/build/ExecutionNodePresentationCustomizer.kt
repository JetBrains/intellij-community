// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Internal
interface ExecutionNodePresentationCustomizer {

  fun getIcon(buildId: Any, node: ExecutionNode): Icon? = null

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<ExecutionNodePresentationCustomizer> =
      ExtensionPointName("com.intellij.executionNodePresentationCustomizer")
  }
}
