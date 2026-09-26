// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.Icon
import javax.swing.ListCellRenderer

@ApiStatus.Internal
@Deprecated("Temporary solution for quick migration from legacy renderers. " +
            "Must be used from Java code only, and only when using the Kotlin UI DSL renderer is too verbose due to Java limitations.")
object LcrJavaHelper {

  /**
   * Use [listCellRenderer] or [textListCellRenderer] in Kotlin code
   */
  @ApiStatus.Internal
  @JvmStatic
  @Deprecated("Temporary solution for quick migration from legacy java renderers.")
  fun <T> create(
    nullValue: @Nls String,
    presentation: (T) -> RendererPresentation,
  ): ListCellRenderer<T?> {
    return listCellRenderer(nullValue) {
      val rendererPresentation = presentation(value)
      rendererPresentation.icon?.let {
        icon(it)
      }
      rendererPresentation.text?.let {
        text(it)
      }
    }
  }
}

@ApiStatus.Internal
@Deprecated("Temporary solution for quick migration from legacy java renderers.")
class RendererPresentation(val icon: Icon?, val text: @Nls String?)
