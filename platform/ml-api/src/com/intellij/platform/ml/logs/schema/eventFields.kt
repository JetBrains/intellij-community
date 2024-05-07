// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.logs.schema

import org.jetbrains.annotations.ApiStatus

/**
 * This event field is an analogue of the event field from FUS's intellij module:
 * [com.intellij.internal.statistic.eventLog.events].
 *
 * Each event field here has only the functionality that is required for ML API.
 *
 * If you want to use these event fields in your application, you must write adapters
 * that would convert these event fields into your event fields.
 * In this example [com.intellij.platform.ml.impl.logs.ComponentAsFusEventRegister]
 * the fields are converted into IJ's FUS fields.
 */
@ApiStatus.Internal
sealed class EventField<T> {
  abstract val name: String
  abstract val description: String?

  infix fun with(data: T): EventPair<T> = EventPair(this, data)
}

@ApiStatus.Internal
class EventPair<T>(val field: EventField<T>, val data: T) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is EventPair<*>) return false

    if (field != other.field) return false
    if (data != other.data) return false

    return true
  }

  override fun hashCode(): Int {
    var result = field.hashCode()
    result = 31 * result + (data?.hashCode() ?: 0)
    return result
  }
}

@ApiStatus.Internal
class StringEventField(override val name: String, override val description: String?, val possibleValues: List<String>) : EventField<String>() {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is StringEventField) return false

    if (name != other.name) return false
    if (description != other.description) return false
    if (possibleValues != other.possibleValues) return false

    return true
  }

  override fun hashCode(): Int {
    var result = name.hashCode()
    result = 31 * result + (description?.hashCode() ?: 0)
    result = 31 * result + possibleValues.hashCode()
    return result
  }
}

@ApiStatus.Internal
class EnumEventField<T : Enum<*>>(
  override val name: String,
  override val description: String?,
  val enumClass: Class<T>,
  val transform: (T) -> String
) : EventField<T>() {
  companion object {
    inline fun <reified T : Enum<*>> of(name: String, description: String?, noinline transform: (T) -> String = Enum<*>::name): EnumEventField<T> {
      return EnumEventField(name, description, T::class.java, transform)
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is EnumEventField<*>) return false

    if (name != other.name) return false
    if (description != other.description) return false
    if (enumClass != other.enumClass) return false
    if (transform != other.transform) return false

    return true
  }

  override fun hashCode(): Int {
    var result = name.hashCode()
    result = 31 * result + (description?.hashCode() ?: 0)
    result = 31 * result + enumClass.hashCode()
    result = 31 * result + transform.hashCode()
    return result
  }
}

@ApiStatus.Internal
class IntEventField(override val name: String, override val description: String?) : EventField<Int>() {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is IntEventField) return false

    if (name != other.name) return false
    if (description != other.description) return false

    return true
  }

  override fun hashCode(): Int {
    var result = name.hashCode()
    result = 31 * result + (description?.hashCode() ?: 0)
    return result
  }
}

@ApiStatus.Internal
class LongEventField(override val name: String,
                     override val description: String?) : EventField<Long>() {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is LongEventField) return false

    if (name != other.name) return false
    if (description != other.description) return false

    return true
  }

  override fun hashCode(): Int {
    var result = name.hashCode()
    result = 31 * result + (description?.hashCode() ?: 0)
    return result
  }
}

@ApiStatus.Internal
class FloatEventField(override val name: String,
                      override val description: String?) : EventField<Float>() {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is FloatEventField) return false

    if (name != other.name) return false
    if (description != other.description) return false

    return true
  }

  override fun hashCode(): Int {
    var result = name.hashCode()
    result = 31 * result + (description?.hashCode() ?: 0)
    return result
  }
}

@ApiStatus.Internal
class DoubleEventField(override val name: String,
                       override val description: String?) : EventField<Double>() {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is DoubleEventField) return false

    if (name != other.name) return false
    if (description != other.description) return false

    return true
  }

  override fun hashCode(): Int {
    var result = name.hashCode()
    result = 31 * result + (description?.hashCode() ?: 0)
    return result
  }
}

@ApiStatus.Internal
class BooleanEventField(override val name: String,
                        override val description: String?) : EventField<Boolean>() {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is BooleanEventField) return false

    if (name != other.name) return false
    if (description != other.description) return false

    return true
  }

  override fun hashCode(): Int {
    var result = name.hashCode()
    result = 31 * result + (description?.hashCode() ?: 0)
    return result
  }
}

@ApiStatus.Internal
class ClassEventField(override val name: String,
                      override val description: String?) : EventField<Class<*>>() {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ClassEventField) return false

    if (name != other.name) return false
    if (description != other.description) return false

    return true
  }

  override fun hashCode(): Int {
    var result = name.hashCode()
    result = 31 * result + (description?.hashCode() ?: 0)
    return result
  }
}

@ApiStatus.Internal
class ObjectEventField(override val name: String,
                       override val description: String?,
                       val objectDescription: ObjectDescription) : EventField<ObjectEventData>()

@ApiStatus.Internal
open class ObjectDescription(fields: List<EventField<*>> = emptyList()) {
  private val eventFields = mutableListOf<EventField<*>>()

  init {
    fields.forEach { field(it) }
  }

  fun <T> field(eventField: EventField<T>) {
    eventFields.add(eventField)
  }

  fun getFields(): List<EventField<*>> {
    return eventFields.toList()
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ObjectDescription) return false

    if (eventFields != other.eventFields) return false

    return true
  }

  override fun hashCode(): Int {
    return eventFields.hashCode()
  }
}

@ApiStatus.Internal
class ObjectEventData(val values: List<EventPair<*>>) {
  constructor(vararg values: EventPair<*>) : this(listOf(*values))

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ObjectEventData) return false

    if (values != other.values) return false

    return true
  }

  override fun hashCode(): Int {
    return values.hashCode()
  }
}

@ApiStatus.Internal
class ObjectListEventField(override val name: String,
                           override val description: String?,
                           val internalObjectDescription: ObjectDescription) : EventField<List<ObjectEventData>>() {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ObjectListEventField) return false

    if (name != other.name) return false
    if (description != other.description) return false
    if (internalObjectDescription != other.internalObjectDescription) return false

    return true
  }

  override fun hashCode(): Int {
    var result = name.hashCode()
    result = 31 * result + (description?.hashCode() ?: 0)
    result = 31 * result + internalObjectDescription.hashCode()
    return result
  }
}

@ApiStatus.Internal
class IntListEventField(override val name: String,
                        override val description: String?) : EventField<List<Int>>()

@ApiStatus.Internal
abstract class CustomRuleEventField<T>(
  override val name: String,
  override val description: String?,
) : EventField<T>() {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is CustomRuleEventField<*>) return false

    if (name != other.name) return false
    if (description != other.description) return false

    return true
  }

  override fun hashCode(): Int {
    var result = name.hashCode()
    result = 31 * result + (description?.hashCode() ?: 0)
    return result
  }
}
