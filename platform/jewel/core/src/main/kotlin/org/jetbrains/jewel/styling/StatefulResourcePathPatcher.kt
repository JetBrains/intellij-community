package org.jetbrains.jewel.styling

import androidx.compose.runtime.Composable
import org.jetbrains.jewel.FocusableComponentState
import org.jetbrains.jewel.IntelliJTheme
import org.jetbrains.jewel.InteractiveComponentState
import org.jetbrains.jewel.SelectableComponentState

class StatefulResourcePathPatcher<T : InteractiveComponentState>(
    private val prefixTokensProvider: (state: T) -> String = { "" },
    private val suffixTokensProvider: (state: T) -> String = { "" },
) : SimpleResourcePathPatcher<T>() {

    @Composable
    override fun injectVariantTokens(extraData: T?): String = buildString {
        if (extraData == null) return@buildString

        append(prefixTokensProvider(extraData))

        if (extraData is SelectableComponentState && extraData.isSelected) {
            append("Selected")
        }

        if (extraData.isEnabled) {
            when {
                extraData is FocusableComponentState && extraData.isFocused -> append("Focused")
                !IntelliJTheme.isSwingCompatMode && extraData.isPressed -> append("Pressed")
                !IntelliJTheme.isSwingCompatMode && extraData.isHovered -> append("Hovered")
            }
        } else {
            append("Disabled")
        }

        append(suffixTokensProvider(extraData))
    }
}
