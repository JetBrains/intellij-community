// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.shared.engine

import com.intellij.platform.rpc.Id
import com.intellij.platform.rpc.UID
import com.intellij.xdebugger.frame.CustomXDescriptorSerializerProvider
import com.intellij.xdebugger.frame.XDescriptor
import fleet.rpc.core.RpcFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
const val JAVA_VALUE_KIND: String = "JavaValue"

// TODO: this class can be refactored,
//  for now its fields are just added adhoc for actions
@ApiStatus.Internal
@Serializable
data class JavaValueDescriptor(
  val isString: Boolean,
  val objectReferenceInfo: JavaValueObjectReferenceInfo?,
  val initialRenderer: NodeRendererDto?,
  val rendererFlow: RpcFlow<NodeRendererDto?>,
  val applicableRenderersFlow: RpcFlow<List<NodeRendererDto>>,
) : XDescriptor {
  override val kind: String = JAVA_VALUE_KIND
}

@ApiStatus.Internal
@Serializable
data class NodeRendererId(override val uid: UID) : Id

@ApiStatus.Internal
@Serializable
data class NodeRendererDto(val id: NodeRendererId, val name: @Nls String)

@ApiStatus.Internal
@Serializable
data class JavaValueObjectReferenceInfo(
  val typeName: String,
  val canGetInstanceInfo: Boolean,
)

private class JavaValueDescriptorSerializerProvider : CustomXDescriptorSerializerProvider {
  override fun getSerializer(kind: String): KSerializer<out XDescriptor>? {
    if (kind == JAVA_VALUE_KIND) {
      return JavaValueDescriptor.serializer()
    }
    return null
  }
}

@ApiStatus.Internal
class JavaValueDescriptorState(val descriptor: JavaValueDescriptor, cs: CoroutineScope) {
  private val _lastRenderer = MutableStateFlow(descriptor.initialRenderer)
  private val _applicableRenderers = MutableStateFlow(emptyList<NodeRendererDto>())

  init {
    cs.launch {
      descriptor.rendererFlow.toFlow().collectLatest {
        _lastRenderer.value = it
      }
    }
    cs.launch {
      descriptor.applicableRenderersFlow.toFlow().collectLatest {
        _applicableRenderers.value = it
      }
    }
  }

  val lastRenderer: NodeRendererDto? get() = _lastRenderer.value
  val applicableRenderers: List<NodeRendererDto> get() = _applicableRenderers.value
}