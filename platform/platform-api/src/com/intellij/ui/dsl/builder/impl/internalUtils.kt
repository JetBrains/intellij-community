// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder.impl

import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.ui.getUserData
import com.intellij.openapi.ui.putUserData
import com.intellij.openapi.util.Key
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.DslComponentProperty
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_WORD_WRAP
import com.intellij.ui.dsl.builder.Row
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@DslMarker
internal annotation class LayoutDslMarker

/**
 * Internal component properties for UI DSL
 */
@ApiStatus.Internal
enum class DslComponentPropertyInternal {
  /**
   * A mark that component is a cell label, see [Cell.label]
   *
   * Value: true
   */
  CELL_LABEL,

  /**
   * Range of allowed integer values in text fields
   *
   * Value: IntRange
   */
  INT_TEXT_RANGE,

  /**
   * Place where the component was added into Kotlin UI DSL builder (for example [Row.cell]). Used in internal mode only
   *
   * Value: Throwable
   */
  CREATION_STACKTRACE,

  /**
   * Preferred columns width for DslLabel when [MAX_LINE_LENGTH_WORD_WRAP] mode is used.
   * A temporary workaround of IJPL-62164 will be removed later.
   *
   * Value: Int
   */
  @Deprecated("Not needed anymore, because IJPL-62164 has been implemented")
  @ApiStatus.ScheduledForRemoval
  PREFERRED_COLUMNS_LABEL_WORD_WRAP
}

private val BOUND_VALUE_PROPERTY_KEY = Key.create<ObservableProperty<*>>("BOUND_VALUE_PROPERTY_KEY")

@get:ApiStatus.Internal
@set:ApiStatus.Internal
var JComponent.defaultValidationRequestor: ObservableProperty<*>?
  get() = getUserData(BOUND_VALUE_PROPERTY_KEY)
  set(value) = putUserData(BOUND_VALUE_PROPERTY_KEY, value)

@ApiStatus.Internal
fun Cell<*>.installValidationRequestor(property: ObservableProperty<*>) {
  component.interactiveComponent.defaultValidationRequestor = property
}

/**
 * See [DslComponentProperty.INTERACTIVE_COMPONENT]
 */
val JComponent.interactiveComponent: JComponent
  @ApiStatus.Internal
  get() {
    val interactiveComponent = getClientProperty(DslComponentProperty.INTERACTIVE_COMPONENT) as JComponent?
    return interactiveComponent ?: this
  }
