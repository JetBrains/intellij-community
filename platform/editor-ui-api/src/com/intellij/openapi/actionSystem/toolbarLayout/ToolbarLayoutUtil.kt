// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.toolbarLayout

import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Component
import java.awt.Container
import java.awt.Dimension

@Internal
public const val RIGHT_ALIGN_KEY = "RIGHT_ALIGN"

/**
 * Calculate the maximum preferred width of the components in the given parent container.
 *
 * @param parent The parent container containing the components.
 * @return The maximum preferred width of the components. Returns 0 if there are no visible components.
 */
internal fun maxComponentPreferredWidth(parent: Container): Int = parent.components
                                                          .filter { it.isVisible }
                                                          .map { it.preferredSize }
                                                          .maxOfOrNull { it.width } ?: 0

/**
 * Calculate the maximum preferred height of the components in the given parent container.
 *
 * @param parent The parent container containing the components.
 * @return The maximum preferred height of the components. Returns 0 if there are no visible components.
 */
internal fun maxComponentPreferredHeight(parent: Container): Int = parent.components
                                                           .filter { it.isVisible }
                                                           .map { it.preferredSize }
                                                           .maxOfOrNull { it.height } ?: 0


/**
 * Retrieves the preferred size of a child component in the given parent container.
 *
 * @param parent The parent container containing the child component.
 * @param index The index of the child component.
 * @return The preferred size of the child component. Returns an empty dimension if the component is not visible.
 */
@Internal
fun getChildPreferredSize(parent: Container, index: Int): Dimension {
  val component: Component = parent.getComponent(index)
  return if (component.isVisible) component.preferredSize else Dimension()
}

@Internal
@JvmOverloads
fun autoLayoutStrategy(showFirst: Boolean = false, noGap: Boolean = false): ToolbarLayoutStrategy = RightActionsAdjusterStrategyWrapper(AutoLayoutStrategy(showFirst, noGap))
