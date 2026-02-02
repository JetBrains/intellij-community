// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.serialization

import kotlinx.serialization.KSerializer
import java.lang.reflect.Modifier

internal object SerializerSearcher {
  fun findSerializer(serializableClass: Class<Any>): KSerializer<Any>? =
    findSerializerInObjectClass(serializableClass) ?:
    findSerializerInCompanionClass(serializableClass)

  private fun findSerializerInObjectClass(objectClass: Class<*>): KSerializer<Any>? {
    val instanceField = objectClass.fields.firstOrNull {
      it.name == "INSTANCE" &&
      it.modifiers.let { Modifier.isPublic(it) && Modifier.isStatic(it) && Modifier.isFinal(it) }
    } ?: return null // not object

    val instance = instanceField.get(null)!!
    return findSerializerInClass(instance, objectClass)
  }

  private fun findSerializerInCompanionClass(serializableClass: Class<*>): KSerializer<Any>? {
    val companionField = serializableClass.getField("Companion")
    companionField.trySetAccessible()
    val companion = companionField.get(null)!!

    return findSerializerInClass(companion, companion.javaClass)
  }

  private fun findSerializerInClass(instance: Any, clazz: Class<*>): KSerializer<Any>? {
    val serializerMethod = clazz.methods.firstOrNull { it.name == "serializer" && it.parameters.isEmpty() } ?: return null
    serializerMethod.trySetAccessible()
    return serializerMethod.invoke(instance) as? KSerializer<Any>
  }
}