// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.SourcePositionProvider.Companion.getSourcePosition
import com.intellij.debugger.engine.withDebugContext
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl
import com.intellij.platform.ide.progress.runWithModalProgressBlocking

internal fun getSourcePositionNow(
  debuggerContext: DebuggerContextImpl,
  descriptor: NodeDescriptorImpl,
): SourcePosition? = runWithModalProgressBlocking(debuggerContext.project, JavaDebuggerBundle.message("compute.field.breakpoint")) {
  withDebugContext(debuggerContext) {
    getSourcePosition(descriptor, debuggerContext.project, debuggerContext)
  }
}
