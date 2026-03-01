// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.shared.rpc

import com.intellij.xdebugger.frame.CustomXDescriptorSerializerProvider
import com.intellij.xdebugger.frame.XDescriptor
import fleet.rpc.core.RpcFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
const val JAVA_SESSION_KIND: String = "JavaSession"

@ApiStatus.Internal
@Serializable
data class JavaDebuggerSessionDto(
  val initialState: JavaSessionState,
  val stateFlow: RpcFlow<JavaSessionState>,
  val areRenderersMutedInitial: Boolean,
  val areRenderersMutedFlow: RpcFlow<Boolean>,
) : XDescriptor {
  override val kind: String = JAVA_SESSION_KIND
}

@ApiStatus.Internal
@Serializable
data class JavaSessionState(
  val isAttached: Boolean,
  val isEvaluationPossible: Boolean,
)

internal class JavaDebuggerSessionDtoSerializerProvider : CustomXDescriptorSerializerProvider {
  override fun getSerializer(kind: String): KSerializer<out XDescriptor>? {
    if (kind != JAVA_SESSION_KIND) return null
    return JavaDebuggerSessionDto.serializer()
  }
}
