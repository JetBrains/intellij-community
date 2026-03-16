// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol

/**
 * Constructs a string representation for a given object with the help of the provided block.
 * Default values are not printed by default.
 *
 * @param name the name or type of the object being represented in string form
 * @param printDefaultValues determines whether fields with default values should be included in the output
 * @param block a lambda function used to define the fields and their values for the string representation
 *
 * @return the constructed string representation of the object
 */
internal fun buildToString(
  name: String,
  printDefaultValues: Boolean = false,
  block: ToStringBuilder.() -> Unit,
): String = buildString {
  append(name)
  append("(")
  ToStringBuilderImpl(this, printDefaultValues).apply(block)
  append(")")
}

internal interface ToStringBuilder {
  /**
   * Appends a field to the string representation with the given name and value.
   * The field does not have a default value.
   */
  fun field(name: String, value: Any?)

  /**
   * Appends a field to the string representation with the given name, value, and default value.
   * If the value is equal to the default value and `printDefaultValues` is off, the field is not included in the string representation.
   */
  fun fieldWithDefault(name: String, value: Any?, default: Any?)

  /**
   * Appends a collection field to the string representation with the given name and value.
   * If the value is empty and `printDefaultValues` is off, the field is not included in the string representation.
   */
  fun fieldWithEmptyDefault(name: String, value: Collection<*>)

  /**
   * Appends a field to the string representation with the given name and value.
   * If the value is `null` and `printDefaultValues` is off, the field is not included in the string representation.
   */
  fun fieldWithNullDefault(name: String, value: Any?) = fieldWithDefault(name, value, null)
}

private class ToStringBuilderImpl(
  private val builder: StringBuilder,
  private val printDefaultValues: Boolean,
) : ToStringBuilder {
  override fun field(name: String, value: Any?) {
    if (builder.last() != '(') {
      builder.append(", ")
    }
    builder.append(name)
    builder.append("=")
    builder.append(value)
  }

  override fun fieldWithEmptyDefault(name: String, value: Collection<*>) {
    if (printDefaultValues || value.isNotEmpty()) {
      field(name, value)
    }
  }

  override fun fieldWithDefault(name: String, value: Any?, default: Any?) {
    if (printDefaultValues || value != default) {
      field(name, value)
    }
  }
}