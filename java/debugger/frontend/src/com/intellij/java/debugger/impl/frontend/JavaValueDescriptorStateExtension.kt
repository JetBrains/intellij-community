// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.frontend

import com.intellij.java.debugger.impl.shared.SharedJavaDebuggerSession
import com.intellij.java.debugger.impl.shared.engine.JavaValueDescriptor
import com.intellij.java.debugger.impl.shared.engine.JavaValueDescriptorState
import com.intellij.java.debugger.impl.shared.rpc.JavaDebuggerSessionDto
import com.intellij.platform.debugger.impl.shared.CustomDescriptorStateManagerExtension
import com.intellij.xdebugger.frame.XDescriptor
import kotlinx.coroutines.CoroutineScope

internal class JavaValueDescriptorStateExtension : CustomDescriptorStateManagerExtension {
  override fun createState(descriptor: XDescriptor, cs: CoroutineScope): Any? {
    if (descriptor !is JavaValueDescriptor) return null
    return JavaValueDescriptorState(descriptor, cs)
  }
}

internal class JavaDebuggerSessionDtoStateExtension : CustomDescriptorStateManagerExtension {
  override fun createState(descriptor: XDescriptor, cs: CoroutineScope): Any? {
    if (descriptor !is JavaDebuggerSessionDto) return null
    return SharedJavaDebuggerSession(descriptor, cs)
  }
}
