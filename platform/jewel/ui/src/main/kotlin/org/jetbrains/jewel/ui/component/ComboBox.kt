package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.onClick
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.takeOrElse
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.modifier.border
import org.jetbrains.jewel.foundation.modifier.onHover
import org.jetbrains.jewel.foundation.modifier.thenIf
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.styling.ComboBoxStyle
import org.jetbrains.jewel.ui.component.styling.PopupContainerStyle
import org.jetbrains.jewel.ui.focusOutline
import org.jetbrains.jewel.ui.outline
import org.jetbrains.jewel.ui.theme.comboBoxStyle
import org.jetbrains.jewel.ui.theme.popupContainerStyle

/**
 * A dropdown component that displays a text label and a popup with custom content.
 *
 * This component provides a standard dropdown UI with a text label. When clicked, it displays a popup with customizable
 * content. Supports keyboard navigation, focus management, and various visual states.
 *
 * It is **strongly** recommended to provide a fixed width for the component, by using modifiers such as `width`,
 * `weight`, `fillMaxWidth`, etc. If the component does not have a fixed width, it will size itself based on the label
 * content. This means the width of the component will change based on the selected item's label.
 *
 * @param labelText The text to display in the dropdown field
 * @param modifier Modifier to be applied to the combo box
 * @param popupModifier Modifier to be applied to the popup
 * @param enabled Controls whether the combo box can be interacted with
 * @param outline The outline style to be applied to the combo box
 * @param maxPopupHeight The maximum height of the popup. If it's unspecified, it will allow the content to grow as
 *   needed
 * @param maxPopupWidth The maximum width of the popup. If it's unspecified, it will allow the content to grow as needed
 * @param interactionSource Source of interactions for this combo box
 * @param style The visual styling configuration for the combo box
 * @param textStyle The typography style to be applied to the text
 * @param onArrowDownPress Called when the down arrow key is pressed while the popup is visible
 * @param onArrowUpPress Called when the up arrow key is pressed while the popup is visible
 * @param popupManager Manager for controlling the popup visibility state
 * @param popupContent Composable content for the popup
 */
@Suppress("UnavailableSymbol") // TODO(JEWEL-983) Address Metalava suppressions
@Composable
public fun ComboBox(
    @Nls labelText: String,
    modifier: Modifier = Modifier,
    popupModifier: Modifier = Modifier,
    enabled: Boolean = true,
    outline: Outline = Outline.None,
    maxPopupHeight: Dp = Dp.Unspecified,
    maxPopupWidth: Dp = Dp.Unspecified,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: ComboBoxStyle = JewelTheme.comboBoxStyle,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
    onArrowDownPress: () -> Unit = {},
    onArrowUpPress: () -> Unit = {},
    // TODO(JEWEL-983) Address Metalava suppressions
    @Suppress("HiddenTypeParameter", "ReferencesHidden") popupManager: PopupManager = remember { PopupManager() },
    popupContent: @Composable () -> Unit,
) {
    ComboBox(
        labelContent = { ComboBoxLabelText(labelText, textStyle, style, enabled) },
        popupContent,
        modifier,
        popupModifier,
        enabled,
        outline,
        maxPopupHeight,
        maxPopupWidth,
        interactionSource,
        style,
        onArrowDownPress,
        onArrowUpPress,
        popupManager,
    )
}

/**
 * A dropdown component that displays a text label and a popup with custom content.
 *
 * This component provides a standard dropdown UI with a text label. When clicked, it displays a popup with customizable
 * content. Supports keyboard navigation, focus management, and various visual states.
 *
 * It is **strongly** recommended to provide a fixed width for the component, by using modifiers such as `width`,
 * `weight`, `fillMaxWidth`, etc. If the component does not have a fixed width, it will size itself based on the label
 * content. This means the width of the component will change based on the selected item's label.
 *
 * @param labelText The text to display in the dropdown field
 * @param modifier Modifier to be applied to the combo box
 * @param popupModifier Modifier to be applied to the popup
 * @param enabled Controls whether the combo box can be interacted with
 * @param outline The outline style to be applied to the combo box
 * @param maxPopupHeight The maximum height of the popup
 * @param interactionSource Source of interactions for this combo box
 * @param style The visual styling configuration for the combo box
 * @param textStyle The typography style to be applied to the text
 * @param onArrowDownPress Called when the down arrow key is pressed while the popup is visible
 * @param onArrowUpPress Called when the up arrow key is pressed while the popup is visible
 * @param popupManager Manager for controlling the popup visibility state
 * @param popupContent Composable content for the popup
 */
@Suppress("UnavailableSymbol") // TODO(JEWEL-983) Address Metalava suppressions
@Composable
@Deprecated("Deprecated in favor of the method with 'maxPopupWidth' parameter", level = DeprecationLevel.HIDDEN)
public fun ComboBox(
    @Nls labelText: String,
    modifier: Modifier = Modifier,
    popupModifier: Modifier = Modifier,
    enabled: Boolean = true,
    outline: Outline = Outline.None,
    maxPopupHeight: Dp = Dp.Unspecified,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: ComboBoxStyle = JewelTheme.comboBoxStyle,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
    onArrowDownPress: () -> Unit = {},
    onArrowUpPress: () -> Unit = {},
    // TODO(JEWEL-983) Address Metalava suppressions
    @Suppress("HiddenTypeParameter", "ReferencesHidden") popupManager: PopupManager = remember { PopupManager() },
    popupContent: @Composable () -> Unit,
) {
    ComboBox(
        labelText = labelText,
        popupContent = popupContent,
        modifier = modifier,
        popupModifier = popupModifier,
        enabled = enabled,
        outline = outline,
        maxPopupHeight = maxPopupHeight,
        maxPopupWidth = Dp.Unspecified,
        interactionSource = interactionSource,
        style = style,
        textStyle = textStyle,
        onArrowDownPress = onArrowDownPress,
        onArrowUpPress = onArrowUpPress,
        popupManager = popupManager,
    )
}

/**
 * A dropdown component that displays custom content in the label area and a popup with custom content.
 *
 * This component provides a standard dropdown UI with customizable label content. When clicked, it displays a popup
 * with customizable content. Supports keyboard navigation, focus management, and various visual states.
 *
 * This version of ComboBox allows for complete customization of the label area through a composable function.
 *
 * It is **strongly** recommended to provide a fixed width for the component, by using modifiers such as `width`,
 * `weight`, `fillMaxWidth`, etc. If the component does not have a fixed width, it will size itself based on the label
 * content. This means the width of the component will change based on the selected item's label.
 *
 * @param labelContent Composable content for the label area of the combo box
 * @param popupContent Composable content for the popup
 * @param modifier Modifier to be applied to the combo box
 * @param popupModifier Modifier to be applied to the popup
 * @param enabled Controls whether the combo box can be interacted with
 * @param outline The outline style to be applied to the combo box
 * @param maxPopupHeight The maximum height of the popup. If it's unspecified, it will allow the content to grow as
 *   needed
 * @param maxPopupWidth The maximum width of the popup. If it's unspecified, it will allow the content to grow as needed
 * @param interactionSource Source of interactions for this combo box
 * @param style The visual styling configuration for the combo box
 * @param onArrowDownPress Called when the down arrow key is pressed while the popup is visible
 * @param onArrowUpPress Called when the up arrow key is pressed while the popup is visible
 * @param popupManager Manager for controlling the popup visibility state
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
@Composable
public fun ComboBox(
    labelContent: @Composable (() -> Unit),
    popupContent: @Composable (() -> Unit),
    modifier: Modifier = Modifier,
    popupModifier: Modifier = Modifier,
    enabled: Boolean = true,
    outline: Outline = Outline.None,
    maxPopupHeight: Dp = Dp.Unspecified,
    maxPopupWidth: Dp = Dp.Unspecified,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: ComboBoxStyle = JewelTheme.comboBoxStyle,
    onArrowDownPress: () -> Unit = {},
    onArrowUpPress: () -> Unit = {},
    popupManager: PopupManager = remember { PopupManager() },
) {
    ComboBox(
        labelContent = labelContent,
        popupContent = popupContent,
        modifier = modifier,
        popupModifier = popupModifier,
        enabled = enabled,
        outline = outline,
        maxPopupHeight = maxPopupHeight,
        maxPopupWidth = maxPopupWidth,
        interactionSource = interactionSource,
        style = style,
        onArrowDownPress = onArrowDownPress,
        onArrowUpPress = onArrowUpPress,
        popupManager = popupManager,
        popupProperties = PopupProperties(focusable = false),
        onPopupKeyEvent = null,
        useIntrinsicPopupWidth = false,
    )
}

/**
 * A dropdown component that displays custom content in the label area and a popup with custom content.
 *
 * This component provides a standard dropdown UI with customizable label content. When clicked, it displays a popup
 * with customizable content. Supports keyboard navigation, focus management, and various visual states.
 *
 * This version of ComboBox allows for complete customization of the label area through a composable function.
 *
 * This variant includes additional parameters for controlling popup behavior, keyboard events, and width measurement.
 *
 * It is **strongly** recommended to provide a fixed width for the component, by using modifiers such as `width`,
 * `weight`, `fillMaxWidth`, etc. If the component does not have a fixed width, it will size itself based on the label
 * content. This means the width of the component will change based on the selected item's label.
 *
 * @param labelContent Composable content for the label area of the combo box
 * @param popupContent Composable content for the popup
 * @param popupProperties Properties controlling the popup window behavior (focusability, dismissal behavior, etc.)
 * @param onPopupKeyEvent Optional callback for handling key events in the popup
 * @param useIntrinsicPopupWidth Whether to use intrinsic width measurement for the popup. Set to true when popup
 *   content needs to size to its widest item (e.g., MenuContent). Set to false (default) when content contains
 *   SubcomposeLayout-based components (LazyColumn, LazyRow, etc.) which don't support intrinsic measurements
 * @param modifier Modifier to be applied to the combo box
 * @param popupModifier Modifier to be applied to the popup
 * @param enabled Controls whether the combo box can be interacted with
 * @param outline The outline style to be applied to the combo box
 * @param maxPopupHeight The maximum height of the popup. If it's unspecified, it will allow the content to grow as
 *   needed
 * @param maxPopupWidth The maximum width of the popup. If it's unspecified, it will allow the content to grow as needed
 * @param interactionSource Source of interactions for this combo box
 * @param style The visual styling configuration for the combo box
 * @param onArrowDownPress Called when the down arrow key is pressed while the popup is visible
 * @param onArrowUpPress Called when the up arrow key is pressed while the popup is visible
 * @param popupManager Manager for controlling the popup visibility state
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
@Composable
public fun ComboBox(
    labelContent: @Composable (() -> Unit),
    popupContent: @Composable (() -> Unit),
    popupProperties: PopupProperties,
    onPopupKeyEvent: ((androidx.compose.ui.input.key.KeyEvent) -> Boolean)?,
    useIntrinsicPopupWidth: Boolean,
    modifier: Modifier = Modifier,
    popupModifier: Modifier = Modifier,
    enabled: Boolean = true,
    outline: Outline = Outline.None,
    maxPopupHeight: Dp = Dp.Unspecified,
    maxPopupWidth: Dp = Dp.Unspecified,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: ComboBoxStyle = JewelTheme.comboBoxStyle,
    onArrowDownPress: () -> Unit = {},
    onArrowUpPress: () -> Unit = {},
    popupManager: PopupManager = remember { PopupManager() },
) {
    ComboBoxImpl(
        labelContent = labelContent,
        popupContent = popupContent,
        modifier = modifier,
        popupModifier = popupModifier,
        enabled = enabled,
        outline = outline,
        maxPopupHeight = maxPopupHeight,
        maxPopupWidth = maxPopupWidth,
        interactionSource = interactionSource,
        style = style,
        onArrowDownPress = onArrowDownPress,
        onArrowUpPress = onArrowUpPress,
        popupManager = popupManager,
        popupProperties = popupProperties,
        onPopupKeyEvent = onPopupKeyEvent,
        useIntrinsicPopupWidth = useIntrinsicPopupWidth,
    )
}

@Composable
internal fun ComboBoxImpl(
    labelContent: @Composable (() -> Unit),
    popupContent: @Composable (() -> Unit),
    modifier: Modifier = Modifier,
    popupModifier: Modifier = Modifier,
    enabled: Boolean = true,
    outline: Outline = Outline.None,
    maxPopupHeight: Dp = Dp.Unspecified,
    maxPopupWidth: Dp = Dp.Unspecified,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: ComboBoxStyle = JewelTheme.comboBoxStyle,
    onArrowDownPress: () -> Unit = {},
    onArrowUpPress: () -> Unit = {},
    popupManager: PopupManager = remember { PopupManager() },
    horizontalPopupAlignment: Alignment.Horizontal = Alignment.Start,
    popupStyle: PopupContainerStyle = JewelTheme.popupContainerStyle,
    popupPositionProvider: PopupPositionProvider =
        AnchorVerticalMenuPositionProvider(
            contentOffset = popupStyle.metrics.offset,
            contentMargin = popupStyle.metrics.menuMargin,
            alignment = horizontalPopupAlignment,
            density = LocalDensity.current,
        ),
    popupProperties: PopupProperties = PopupProperties(focusable = false),
    onPopupKeyEvent: ((androidx.compose.ui.input.key.KeyEvent) -> Boolean)? = null,
    useIntrinsicPopupWidth: Boolean = false,
) {
    var chevronHovered by remember { mutableStateOf(false) }

    val popupVisible by popupManager.isPopupVisible

    var comboBoxState by remember { mutableStateOf(ComboBoxState.of(enabled = enabled)) }
    val comboBoxFocusRequester = remember { FocusRequester() }

    remember(enabled) { comboBoxState = comboBoxState.copy(enabled = enabled) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> comboBoxState = comboBoxState.copy(pressed = true)
                is PressInteraction.Cancel,
                is PressInteraction.Release -> comboBoxState = comboBoxState.copy(pressed = false)
                is HoverInteraction.Enter -> comboBoxState = comboBoxState.copy(hovered = true)
                is HoverInteraction.Exit -> comboBoxState = comboBoxState.copy(hovered = false)
            }
        }
    }

    val shape = RoundedCornerShape(style.metrics.cornerSize)
    val borderColor by style.colors.borderFor(comboBoxState)
    var comboBoxWidth by remember { mutableStateOf(Dp.Unspecified) }
    val density = LocalDensity.current

    Row(
        modifier =
            modifier
                .focusRequester(comboBoxFocusRequester)
                .onFocusChanged { focusState ->
                    comboBoxState = comboBoxState.copy(focused = focusState.isFocused)
                    if (!focusState.isFocused) {
                        popupManager.setPopupVisible(false)
                    }
                }
                .thenIf(enabled) {
                    focusable(true, interactionSource)
                        .onHover { chevronHovered = it }
                        .pointerInput(interactionSource) {
                            detectPressAndCancel(
                                onPress = {
                                    popupManager.setPopupVisible(!popupVisible)
                                    comboBoxFocusRequester.requestFocus()
                                },
                                onCancel = { popupManager.setPopupVisible(false) },
                            )
                        }
                        .semantics(mergeDescendants = true) { role = Role.DropdownList }
                        .onPreviewKeyEvent {
                            if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                            when {
                                it.key == Key.Spacebar -> {
                                    popupManager.setPopupVisible(!popupVisible)
                                    true
                                }

                                it.key == Key.DirectionDown -> {
                                    if (popupVisible) {
                                        onArrowDownPress()
                                    } else {
                                        popupManager.setPopupVisible(true)
                                    }
                                    true
                                }

                                it.key == Key.DirectionUp && popupVisible -> {
                                    onArrowUpPress()
                                    true
                                }

                                it.key == Key.Escape && popupVisible -> {
                                    popupManager.setPopupVisible(false)
                                    true
                                }

                                else -> false
                            }
                        }
                }
                .background(style.colors.backgroundFor(comboBoxState, false).value, shape)
                .thenIf(outline == Outline.None) {
                    focusOutline(state = comboBoxState, outlineShape = shape, alignment = Stroke.Alignment.Center)
                        .border(
                            alignment = Stroke.Alignment.Inside,
                            width = style.metrics.borderWidth,
                            color = borderColor,
                            shape = shape,
                        )
                }
                .outline(
                    state = comboBoxState,
                    outline = outline,
                    outlineShape = shape,
                    alignment = Stroke.Alignment.Center,
                )
                .widthIn(min = style.metrics.minSize.width)
                .height(style.metrics.minSize.height)
                .onSizeChanged { comboBoxWidth = with(density) { it.width.toDp() } },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        CompositionLocalProvider(LocalContentColor provides style.colors.contentFor(comboBoxState).value) {
            Box(
                modifier = Modifier.height(style.metrics.minSize.height).weight(1f, fill = false),
                contentAlignment = Alignment.CenterStart,
            ) {
                labelContent()
            }

            if (popupVisible) {
                val maxHeight = maxPopupHeight.takeOrElse { style.metrics.maxPopupHeight }

                PopupContainer(
                    onDismissRequest = {
                        if (!chevronHovered) {
                            popupManager.setPopupVisible(false)
                        }
                    },
                    modifier =
                        Modifier.testTag("Jewel.ComboBox.Popup")
                            .heightIn(max = maxHeight)
                            .widthIn(min = comboBoxWidth, max = maxPopupWidth.coerceAtLeast(comboBoxWidth))
                            .then(popupModifier)
                            .onClick { popupManager.setPopupVisible(false) },
                    horizontalAlignment = horizontalPopupAlignment,
                    useIntrinsicWidth = useIntrinsicPopupWidth,
                    popupProperties = popupProperties,
                    style = popupStyle,
                    popupPositionProvider = popupPositionProvider,
                    onKeyEvent = onPopupKeyEvent,
                    content = popupContent,
                )
            }

            Chevron(style, enabled, modifier = Modifier)
        }
    }
}

/**
 * A dropdown component that displays custom content in the label area and a popup with custom content.
 *
 * This component provides a standard dropdown UI with customizable label content. When clicked, it displays a popup
 * with customizable content. Supports keyboard navigation, focus management, and various visual states.
 *
 * This version of ComboBox allows for complete customization of the label area through a composable function.
 *
 * It is **strongly** recommended to provide a fixed width for the component, by using modifiers such as `width`,
 * `weight`, `fillMaxWidth`, etc. If the component does not have a fixed width, it will size itself based on the label
 * content. This means the width of the component will change based on the selected item's label.
 *
 * @param labelContent Composable content for the label area of the combo box
 * @param popupContent Composable content for the popup
 * @param modifier Modifier to be applied to the combo box
 * @param popupModifier Modifier to be applied to the popup
 * @param enabled Controls whether the combo box can be interacted with
 * @param outline The outline style to be applied to the combo box
 * @param maxPopupHeight The maximum height of the popup
 * @param interactionSource Source of interactions for this combo box
 * @param style The visual styling configuration for the combo box
 * @param onArrowDownPress Called when the down arrow key is pressed while the popup is visible
 * @param onArrowUpPress Called when the up arrow key is pressed while the popup is visible
 * @param popupManager Manager for controlling the popup visibility state
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
@Composable
@Deprecated("Deprecated in favor of the method with 'maxPopupWidth' parameter", level = DeprecationLevel.HIDDEN)
public fun ComboBox(
    labelContent: @Composable (() -> Unit),
    popupContent: @Composable (() -> Unit),
    modifier: Modifier = Modifier,
    popupModifier: Modifier = Modifier,
    enabled: Boolean = true,
    outline: Outline = Outline.None,
    maxPopupHeight: Dp = Dp.Unspecified,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: ComboBoxStyle = JewelTheme.comboBoxStyle,
    onArrowDownPress: () -> Unit = {},
    onArrowUpPress: () -> Unit = {},
    popupManager: PopupManager = remember { PopupManager() },
) {
    ComboBox(
        labelContent = labelContent,
        popupContent = popupContent,
        modifier = modifier,
        popupModifier = popupModifier,
        enabled = enabled,
        outline = outline,
        maxPopupHeight = maxPopupHeight,
        maxPopupWidth = Dp.Unspecified,
        interactionSource = interactionSource,
        style = style,
        onArrowDownPress = onArrowDownPress,
        onArrowUpPress = onArrowUpPress,
        popupManager = popupManager,
    )
}

@Composable
internal fun ComboBoxLabelText(text: String, style: TextStyle, comboBoxStyle: ComboBoxStyle, enabled: Boolean) {
    val textColor = if (enabled) Color.Unspecified else comboBoxStyle.colors.borderDisabled

    Text(
        text = text,
        style = style.copy(color = textColor),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.testTag("Jewel.ComboBox.NonEditableText").padding(comboBoxStyle.metrics.contentPadding),
    )
}

/**
 * Renders the chevron (down arrow) icon for the combo box.
 *
 * @param style The visual styling configuration for the combo box
 * @param enabled Whether the combo box is enabled, affects the icon color
 */
@Composable
private fun Chevron(style: ComboBoxStyle, enabled: Boolean, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.testTag("Jewel.ComboBox.ChevronContainer").size(style.metrics.arrowAreaSize).then(modifier),
    ) {
        val iconColor = if (enabled) Color.Unspecified else style.colors.borderDisabled
        Icon(key = style.icons.chevronDown, tint = iconColor, contentDescription = null)
    }
}
