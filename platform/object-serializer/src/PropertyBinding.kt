// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization

internal data class PropertyBinding(private val binding: RootBinding) : NestedBinding {
  override fun serialize(obj: Any, context: WriteContext) {
    binding.serialize(obj, context)
  }

  override fun deserialize(context: ReadContext) = binding.deserialize(context)

  override fun serialize(hostObject: Any, property: MutableAccessor, context: WriteContext) {
    write(hostObject, property, context) {
      binding.serialize(it, context)
    }
  }

  override fun deserialize(hostObject: Any, property: MutableAccessor, context: ReadContext) {
    read(hostObject, property, context) {
      binding.deserialize(context)
    }
  }
}