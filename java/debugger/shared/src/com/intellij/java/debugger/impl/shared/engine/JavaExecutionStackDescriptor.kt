// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.shared.engine

import com.intellij.xdebugger.frame.CustomXDescriptorSerializerProvider
import com.intellij.xdebugger.frame.XDescriptor
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
const val JAVA_EXECUTION_STACK_KIND: String = "JavaExecutionStack"

@ApiStatus.Internal
@Serializable
data class JavaExecutionStackDescriptor(
  val isSuspended: Boolean
) : XDescriptor {
  override val kind: String = JAVA_EXECUTION_STACK_KIND
}

private class JavaExecutionStackDescriptorSerializerProvider : CustomXDescriptorSerializerProvider {
  override fun getSerializer(kind: String): KSerializer<out XDescriptor>? {
    if (kind == JAVA_EXECUTION_STACK_KIND) {
      return JavaExecutionStackDescriptor.serializer()
    }
    return null
  }
}