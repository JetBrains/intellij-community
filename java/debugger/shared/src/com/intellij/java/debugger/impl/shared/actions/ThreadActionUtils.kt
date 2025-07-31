// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.shared.actions

import com.intellij.java.debugger.impl.shared.engine.JavaExecutionStackDescriptor
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.impl.frame.XThreadsView
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl

internal fun XValueNodeImpl.isSuspendedJavaThread(): Boolean =
  javaExecutionStackDescriptorOrNull?.isSuspended == true

internal fun XValueNodeImpl.isNotSuspendedJavaThread(): Boolean =
  javaExecutionStackDescriptorOrNull?.isSuspended == false

private val XValueNodeImpl.javaExecutionStackDescriptorOrNull: JavaExecutionStackDescriptor?
  get() = (valueContainer as? XThreadsView.FramesContainer)?.executionStack?.xExecutionStackDescriptorAsync?.getNow(null) as? JavaExecutionStackDescriptor

internal val XValueNodeImpl.executionStackOrNull: XExecutionStack?
  get() = (valueContainer as? XThreadsView.FramesContainer)?.executionStack