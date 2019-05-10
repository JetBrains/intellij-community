// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.serialization

import java.lang.reflect.Type

interface Binding {
  fun init(originalType: Type, context: BindingInitializationContext) {
  }
}

/**
 * Binding that can read and write data of object properties.
 */
interface NestedBinding : Binding {
  fun deserialize(hostObject: Any, property: MutableAccessor, context: ReadContext)

  fun serialize(hostObject: Any, property: MutableAccessor, context: WriteContext)
}

/**
 * Binding that can read and write data of root object.
 */
interface RootBinding : Binding {
  fun serialize(obj: Any, context: WriteContext)

  fun deserialize(context: ReadContext): Any
}

interface BindingInitializationContext {
  val propertyCollector: PropertyCollector
  val bindingProducer: BindingProducer<RootBinding>
}