// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder.impl

import com.intellij.idea.AppMode
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.getUserData
import com.intellij.openapi.ui.putUserData
import com.intellij.openapi.util.Key
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.DslComponentProperty
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_WORD_WRAP
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.gridLayout.Constraints
import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.nio.file.Files
import javax.swing.JComponent
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

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

@ApiStatus.Internal
val LOG: Logger = Logger.getInstance("JetBrains UI DSL")

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

@OptIn(ExperimentalContracts::class)
@ApiStatus.Internal
fun checkNull(value: Any?) {
  contract {
    returns() implies (value == null)
  }
  checkNull(value) { "Value must be null." }
}

@OptIn(ExperimentalContracts::class)
@ApiStatus.Internal
fun checkNull(value: Any?, lazyMessage: () -> String) {
  contract {
    returns() implies (value == null)
  }
  check(value == null, lazyMessage)
}

@ApiStatus.Internal
fun checkNonNegative(name: String, value: Int) {
  check(value >= 0) { "Value cannot be negative: $name = $value" }
}

@ApiStatus.Internal
fun checkPositive(name: String, value: Int) {
  check(value > 0) { "Value must be positive: $name = $value" }
}

@OptIn(ExperimentalContracts::class)
@ApiStatus.Internal
fun checkJComponent(component: Component?): JComponent {
  contract {
    returns() implies (component is JComponent)
  }
  check(component is JComponent) { "Only JComponents are supported: $component" }
  return component
}

@OptIn(ExperimentalContracts::class)
@ApiStatus.Internal
fun checkConstraints(constraints: Any?): Constraints {
  contract {
    returns() implies (constraints is Constraints)
  }
  check(constraints is Constraints) { "Invalid constraints: $constraints" }
  return constraints
}

/**
 * Throws an exception when running in debug mode; otherwise, logs the error
 */
@ApiStatus.Internal
fun failInDebugOrLog(message: String) {
  if (strictValidation) {
    error(message)
  }
  else {
    LOG.error(message)
  }
}

@ApiStatus.Internal
fun logWarningWithDebugStacktrace(message: String) {
  if (strictValidation) {
    LOG.warn(message, Throwable())
  }
  else {
    LOG.warn(message)
  }
}

private val strictValidation: Boolean by lazy {
  // See [com.intellij.ide.plugins.PluginManagerCore.isRunningFromSources]
  // MPS is always loading platform classes from jars even though there is a project directory present
  val runningFromSource = !PlatformUtils.isMPS() && Files.isDirectory(PathManager.getHomeDir().resolve(Project.DIRECTORY_STORE_FOLDER))
  val testMode = java.lang.Boolean.getBoolean("idea.is.unit.test") || java.lang.Boolean.getBoolean("idea.is.integration.test")

  runningFromSource || AppMode.isRunningFromDevBuild() || testMode
}
