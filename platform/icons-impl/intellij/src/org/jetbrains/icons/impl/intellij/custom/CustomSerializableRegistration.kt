// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.intellij.custom

import com.intellij.openapi.extensions.ExtensionPointName
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModuleBuilder
import kotlin.reflect.KClass

abstract class CustomSerializableRegistration<TImplementation: Any>(
  internal val klass: KClass<TImplementation>
) {
  abstract fun createSerializer(): KSerializer<TImplementation>

  abstract class Companion<TBase: Any, TEP: CustomSerializableRegistration<*>>(
    private val baseClass: KClass<TBase>,
    extensionName: String
  ) {
    fun registerSerializersTo(builder: SerializersModuleBuilder) {
      EP_NAME.extensionList.forEach { instance ->
        val serializer = instance.createSerializer()
        @Suppress("UNCHECKED_CAST")
        builder.addCustom(instance as CustomSerializableRegistration<TBase>)
        onRegistration(instance, serializer)
      }
    }

    private fun <T: TBase> SerializersModuleBuilder.addCustom(instance: CustomSerializableRegistration<T>) {
      polymorphic(
        baseClass,
        instance.klass,
        instance.createSerializer()
      )
    }

    open fun onRegistration(ep: TEP, serializer: KSerializer<*>) {
      // Do nothing in default implementation
    }

    val EP_NAME: ExtensionPointName<TEP> = ExtensionPointName(extensionName)
  }
}