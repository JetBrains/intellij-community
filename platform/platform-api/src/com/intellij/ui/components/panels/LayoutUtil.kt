// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components.panels

import java.awt.Component
import java.awt.Dimension

internal object LayoutUtil {
    /**
     * @param component the component whose size is calculated
     * @return the preferred size of the given component based on its maximum size
     */
    @JvmStatic
    fun getPreferredSize(component: Component): Dimension {
        val size = component.preferredSize ?: return Dimension() // rare

        if (component.isMaximumSizeSet) {
            val max = component.getMaximumSize()
            if (max != null) {
                if (size.width > max.width) size.width = max.width
                if (size.height > max.height) size.height = max.height
            }
        }
        return size
    }
}
