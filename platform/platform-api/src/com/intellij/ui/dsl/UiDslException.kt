// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.gridLayout.Constraints
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.nio.file.Files
import java.nio.file.Paths
import javax.swing.JComponent

@ApiStatus.Internal
class UiDslException(message: String = "Internal error", cause: Throwable? = null) : RuntimeException(message, cause) {

  companion object {
    fun error(message: String) {
      if (runningFromSource) {
        throw UiDslException(message)
      }
      else {
        logger<UiDslException>().error(message)
      }
    }
  }
}

@ApiStatus.Internal
fun checkTrue(value: Boolean) {
  if (!value) {
    throw UiDslException()
  }
}

@ApiStatus.Internal
fun checkNonNegative(name: String, value: Int) {
  if (value < 0) {
    throw UiDslException("Value cannot be negative: $name = $value")
  }
}

@ApiStatus.Internal
fun checkPositive(name: String, value: Int) {
  if (value <= 0) {
    throw UiDslException("Value must be positive: $name = $value")
  }
}

@ApiStatus.Internal
fun checkComponent(component: Component?): JComponent {
  if (component !is JComponent) {
    throw UiDslException("Only JComponents are supported: $component")
  }

  return component
}

@ApiStatus.Internal
fun checkConstraints(constraints: Any?): Constraints {
  if (constraints !is Constraints) {
    throw UiDslException("Invalid constraints: $constraints")
  }

  return constraints
}

/**
 * See [com.intellij.ide.plugins.PluginManagerCore.isRunningFromSources], which is not available here
 */
val runningFromSource: Boolean by lazy(LazyThreadSafetyMode.PUBLICATION) {
  Files.isDirectory(Paths.get(PathManager.getHomePath(), Project.DIRECTORY_STORE_FOLDER))
}
