// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer

import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.annotations.ApiStatus
import javax.swing.ListCellRenderer

/**
 * Provides cell renderer for DSL UI lists (@see [com.intellij.ui.dsl.listCellRenderer.listCellRenderer]
 */
@ApiStatus.Internal
interface UiDslRendererProvider {

  companion object {
    fun getInstance(): UiDslRendererProvider {
      return ApplicationManager.getApplication().getService(UiDslRendererProvider::class.java)
             ?: throw IllegalStateException("No UiDslRendererProvider service found")
    }
  }

  /**
   * Retrieves a `ListCellRenderer` for rendering the content of an `LcrRow`.
   *
   * @param renderContent A lambda expression representing a function that takes an `LcrRow` and returns `Unit`.
   *                      This function is responsible for providing the content of the row.
   * @return The `ListCellRenderer` to be used for rendering the content of an `LcrRow`.
   */
  fun <T> getLcrRenderer(renderContent: LcrRow<T>.() -> Unit): ListCellRenderer<T>
}
