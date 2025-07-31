// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.shared.engine

import com.intellij.xdebugger.frame.CustomXDescriptorSerializerProvider
import com.intellij.xdebugger.frame.XDescriptor
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
const val JAVA_VALUE_KIND: String = "JavaValue"

// TODO: this class can be refactored,
//  for now its fields are just added adhoc for actions
@ApiStatus.Internal
@Serializable
data class JavaValueDescriptor(
  val isString: Boolean,
  val objectReferenceInfo: JavaValueObjectReferenceInfo?,
) : XDescriptor {
  override val kind: String = JAVA_VALUE_KIND
}

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