// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing.components.style

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.style.SwingComponentStyleScope
import org.jetbrains.compose.swing.style.stylePropertyOf
import javax.swing.JComponent

/**
 * How a field is outlined when its value is not accepted.
 *
 * @see com.intellij.ide.ui.laf.darcula.DarculaUIUtil.Outline
 */
@ApiStatus.Experimental
public enum class Outline(internal val propertyValue: String) {
  ERROR("error"),
  WARNING("warning"),
}

/**
 * Outlines a field the way the IDE outlines one whose value is not accepted, or clears the outline when
 * [outline] is `null`.
 *
 * This is the look; the page still refuses the value by throwing
 * [com.intellij.openapi.options.ConfigurationException] from `apply`, which is what keeps the Settings
 * dialog open and shows the message.
 *
 * @see com.intellij.ide.ui.laf.darcula.DarculaUIUtil.Outline
 * @see com.intellij.ui.dsl.builder.Cell.validationOnApply
 */
@ApiStatus.Experimental
public fun SwingComponentStyleScope.outline(outline: Outline?): Unit = OutlineProperty.provide(outline?.propertyValue)

/**
 * Outlines a field as holding a value that is not accepted while [hasError] is `true`.
 *
 * @see com.intellij.ide.ui.laf.darcula.DarculaUIUtil.Outline.error
 */
@ApiStatus.Experimental
public fun SwingComponentStyleScope.errorOutline(hasError: Boolean): Unit = outline(if (hasError) Outline.ERROR else null)

private const val OUTLINE_KEY = "JComponent.outline"

/** The client property the IDE look and feel reads to paint the outline. */
private val OutlineProperty =
  stylePropertyOf(
    ComponentPropertyDescriptor<JComponent, String?>(
      name = OUTLINE_KEY,
      read = { it.getClientProperty(OUTLINE_KEY) as? String },
      write = { component, value -> component.putClientProperty(OUTLINE_KEY, value) },
    ),
  )
