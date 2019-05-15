// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization

import java.lang.reflect.Type

interface Binding {
}

/**
 * Binding that can read and write data of object properties.
 *
 * Each NestedBinding also is a [RootBinding] not only because of ability to serialize any type as root (including primitives),
 * but because of support for non-default class constructors - for this case BeanBinding will use nested binding as root binding (read value without passing property).
 */
interface NestedBinding : RootBinding {
  fun deserialize(hostObject: Any, property: MutableAccessor, context: ReadContext)

  fun serialize(hostObject: Any, property: MutableAccessor, context: WriteContext)
}

/**
 * Binding that can read and write data of root object.
 */
interface RootBinding : Binding {
  fun init(originalType: Type, context: BindingInitializationContext) {
  }

  fun serialize(obj: Any, context: WriteContext)

  fun deserialize(context: ReadContext): Any
}

interface BindingInitializationContext {
  val propertyCollector: PropertyCollector
  val bindingProducer: BindingProducer<RootBinding>

  val isResolveConstructorOnInit: Boolean
    get() = false
}