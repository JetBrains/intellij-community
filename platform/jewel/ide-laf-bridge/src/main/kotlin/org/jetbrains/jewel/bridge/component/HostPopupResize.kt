// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.ui.scale.JBUIScale
import java.awt.Dimension
import javax.swing.SwingUtilities
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.LocalComponent

/**
 * Keeps the hosting `JewelComposePanel` and its [JBPopup] sized to fit the Compose content. To keep a dimension fixed,
 * constrain it in the modifier chain (e.g. `.width(450.dp)`); unconstrained dimensions grow with the content.
 *
 * Example:
 * ```kotlin
 * Column(
 *     modifier = Modifier
 *         .resizeHostOnContentSizeChange(popup = { popupRef })
 *         .width(450.dp)
 * ) { ... }
 * ```
 *
 * @param popup the popup hosting the panel; its `size` is kept in sync with the content. Invoked lazily so the modifier
 *   can be created before the popup reference is assigned.
 * @param onResize runs on the EDT after the resize is applied, e.g. for popup repositioning.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
@Composable
public fun Modifier.resizeHostOnContentSizeChange(popup: () -> JBPopup?, onResize: (Dimension) -> Unit = {}): Modifier {
    val host = LocalComponent.current
    val lastDp = remember { intArrayOf(-1, -1) }
    return this.layout { measurable, constraints ->
        val relaxed =
            constraints.copy(
                minWidth = 0,
                maxWidth = Constraints.Infinity,
                minHeight = 0,
                maxHeight = Constraints.Infinity,
            )
        val placeable = measurable.measure(relaxed)
        val scale = JBUIScale.sysScale(host)
        val widthDp = (placeable.width / scale).toInt()
        val heightDp = (placeable.height / scale).toInt()
        if (widthDp != lastDp[0] || heightDp != lastDp[1]) {
            lastDp[0] = widthDp
            lastDp[1] = heightDp
            val newSize = Dimension(widthDp, heightDp)
            SwingUtilities.invokeLater {
                host.preferredSize = newSize
                popup()?.size = newSize
                onResize(newSize)
            }
        }
        layout(
            placeable.width.coerceIn(constraints.minWidth, constraints.maxWidth),
            placeable.height.coerceIn(constraints.minHeight, constraints.maxHeight),
        ) {
            placeable.placeRelative(0, 0)
        }
    }
}
