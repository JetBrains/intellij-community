// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.PopupProperties
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.styling.ComboBoxStyle
import org.jetbrains.jewel.ui.component.styling.LocalMenuStyle
import org.jetbrains.jewel.ui.component.styling.MenuStyle
import org.jetbrains.jewel.ui.theme.comboBoxStyle
import org.jetbrains.jewel.ui.theme.menuStyle

/**
 * A dropdown component that displays custom label content and shows menu items in the popup.
 *
 * This component combines the [ComboBox] UI with [MenuContent] for the popup, providing a dropdown that displays
 * standard menu items (with icons, keybindings, submenus, etc.) when opened.
 *
 * This is the modern replacement for the deprecated [Dropdown] component, providing better keyboard navigation, focus
 * management, and accessibility support.
 *
 * It is **strongly** recommended to provide a fixed width for the component, by using modifiers such as `width`,
 * `weight`, `fillMaxWidth`, etc. If the component does not have a fixed width, it will size itself based on the label
 * content.
 *
 * **Guidelines:** [on IJP SDK webhelp](https://plugins.jetbrains.com/docs/intellij/drop-down.html)
 *
 * @param labelContent Composable content for the label area of the combo box
 * @param modifier Modifier to be applied to the combo box
 * @param popupModifier Modifier to be applied to the popup
 * @param enabled Controls whether the combo box can be interacted with
 * @param outline The outline style to be applied to the combo box
 * @param maxPopupHeight The maximum height of the popup
 * @param maxPopupWidth The maximum width of the popup. If not set, it will match the width of the combo box
 * @param interactionSource Source of interactions for this combo box
 * @param style The visual styling configuration for the combo box
 * @param menuStyle The visual styling configuration for the menu content
 * @param onPopupVisibleChange Called when the popup visibility changes
 * @param content The menu items to display in the popup using [MenuScope] DSL
 * @see Dropdown
 * @see ComboBox
 * @see ListComboBox
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
@Composable
public fun MenuComboBox(
    labelContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    popupModifier: Modifier = Modifier,
    enabled: Boolean = true,
    outline: Outline = Outline.None,
    maxPopupHeight: Dp = Dp.Unspecified,
    maxPopupWidth: Dp = Dp.Unspecified,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: ComboBoxStyle = JewelTheme.comboBoxStyle,
    menuStyle: MenuStyle = JewelTheme.menuStyle,
    onPopupVisibleChange: (visible: Boolean) -> Unit = {},
    content: MenuScope.() -> Unit,
) {
    val popupManager = remember { PopupManager(onPopupVisibleChange) }
    val menuController = remember {
        DefaultMenuController(
            onDismissRequest = {
                popupManager.setPopupVisible(false)
                true
            }
        )
    }

    var focusManager: FocusManager? by remember { mutableStateOf(null) }
    var inputModeManager: InputModeManager? by remember { mutableStateOf(null) }

    ComboBox(
        labelContent = labelContent,
        popupContent = {
            @Suppress("AssignedValueIsNeverRead")
            focusManager = LocalFocusManager.current
            @Suppress("AssignedValueIsNeverRead")
            inputModeManager = LocalInputModeManager.current

            CompositionLocalProvider(LocalMenuController provides menuController, LocalMenuStyle provides menuStyle) {
                MenuContent(
                    modifier = popupModifier,
                    style = menuStyle,
                    useFullWidthSelection = true,
                    content = content,
                )
            }
        },
        popupProperties = PopupProperties(focusable = true),
        onPopupKeyEvent = { keyEvent ->
            val currentFocusManager = focusManager ?: return@ComboBox false
            val currentInputModeManager = inputModeManager ?: return@ComboBox false

            handlePopupMenuOnKeyEvent(keyEvent, currentFocusManager, currentInputModeManager, menuController)
        },
        useIntrinsicPopupWidth = true,
        modifier = modifier,
        popupModifier = Modifier,
        enabled = enabled,
        outline = outline,
        maxPopupHeight = maxPopupHeight,
        maxPopupWidth = maxPopupWidth,
        interactionSource = interactionSource,
        style = style,
        popupManager = popupManager,
    )
}
