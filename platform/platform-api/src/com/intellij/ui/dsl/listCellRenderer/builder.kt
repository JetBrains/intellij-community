// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.ListCellRenderer

@DslMarker
internal annotation class LcrDslMarker

/**
 * Builds [ListCellRenderer], which can contains several cells with texts, icons and other entities placed in one row.
 * Covers the most common kinds of renderers and provides all necessary functionality:
 *
 * * Rectangular selection and correct insets for old UI
 * * Rounded selection (including multi-selection), correct insets and height for new UI
 * * Uses correct colors for text in selected/unselected state
 * * Uses gray text and icons in disabled state
 * * Colored text has different color in selected state
 * * Supports IDE scaling and compact mode
 * * Provides accessibility details for rows: by default, it is concatenation of accessible names of all visible cells
 * * Supports `Copy` feature for selected item(-s)
 * * Supports `Search` feature in the settings dialog (can be used from search everywhere as well)
 *
 * Because of all described nuances, it is hard to write correct own render. So using Kotlin UI DSL is highly recommended
 */
fun <T> listCellRenderer(init: LcrRow<T>.() -> Unit): ListCellRenderer<T> {
  return UiDslRendererProvider.getInstance().getLcrRenderer(init)
}

@ApiStatus.Internal
fun <T> listCellRenderer(nullValue: @Nls String, init: LcrRow<T>.() -> Unit): ListCellRenderer<T?> {
  return listCellRenderer {
    if (value == null) {
      text(nullValue)
    } else {
      @Suppress("UNCHECKED_CAST")
      (this as LcrRow<T>).init()
    }
  }
}

/**
 * Simplified version of [listCellRenderer] with one text cell
 */
fun <T> textListCellRenderer(textExtractor: (T) -> @Nls String?): ListCellRenderer<T> {
  return listCellRenderer {
    text(textExtractor(value) ?: "")
  }
}

@ApiStatus.Internal
fun <T> textListCellRenderer(nullValue: @Nls String, textExtractor: (T) -> @Nls String?): ListCellRenderer<T?> {
  return listCellRenderer {
    val value = value
    if (value == null) {
      text(nullValue)
    }
    else {
      text(textExtractor(value) ?: nullValue)
    }
  }
}

/**
 * A version of [textListCellRenderer] with separator support
 */
@ApiStatus.Internal
fun <T> groupedTextListCellRenderer(textExtractor: (T) -> @Nls String?, separatorExtractor: (T) -> String?): ListCellRenderer<T> {
  return listCellRenderer {
    text(textExtractor(value) ?: "")
    separatorExtractor(value)?.let {
      separator {
        text = it
      }
    }
  }
}
