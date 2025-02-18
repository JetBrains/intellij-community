// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.shared.engine

import com.intellij.xdebugger.frame.XValueCustomDescriptorSerializerProvider
import com.intellij.xdebugger.frame.XValueDescriptor
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.subclass
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
const val JAVA_VALUE_KIND: String = "JavaValue"

@ApiStatus.Internal
@Serializable
class JavaValueDescriptor(
  val isString: Boolean,
) : XValueDescriptor {
  override val kind: String = JAVA_VALUE_KIND
}

private class JavaValueDescriptorSerializerProvider : XValueCustomDescriptorSerializerProvider {
  override fun registerSerializer(builder: PolymorphicModuleBuilder<XValueDescriptor>) {
    builder.subclass(JavaValueDescriptor::class)
  }
}