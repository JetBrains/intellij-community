// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.debugger.memory.utils.StackFrameItem
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface CreationStackTraceProvider {
  fun getCreationStackTrace(stackFrame: JavaStackFrame, suspendContext: SuspendContextImpl): List<StackFrameItem?>?

  companion object {
    @JvmField
    val EP: ExtensionPointName<CreationStackTraceProvider> = ExtensionPointName.create("com.intellij.debugger.creationStackTraceProvider")
  }
}