package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.modifier.border
import org.jetbrains.jewel.foundation.modifier.onHover
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Active
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Enabled
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Focused
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Hovered
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Pressed
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Selected
import org.jetbrains.jewel.foundation.state.FocusableComponentState
import org.jetbrains.jewel.foundation.state.SelectableComponentState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.foundation.theme.LocalTextStyle
import org.jetbrains.jewel.foundation.theme.OverrideDarkMode
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.styling.LocalMenuStyle
import org.jetbrains.jewel.ui.component.styling.MenuItemColors
import org.jetbrains.jewel.ui.component.styling.MenuItemMetrics
import org.jetbrains.jewel.ui.component.styling.MenuStyle
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.painter.hints.Stateful
import org.jetbrains.jewel.ui.theme.menuStyle
import org.jetbrains.skiko.hostOs

/**
 * A popup menu component that follows the standard visual styling with customizable content.
 *
 * Provides a floating menu that can be used for context menus, dropdown menus, and other popup menu scenarios. The menu
 * supports keyboard navigation, icons, keybindings, and nested submenus.
 *
 * **Guidelines:** [on IJP SDK webhelp](https://plugins.jetbrains.com/docs/intellij/popups-and-menus.html)
 *
 * **Swing equivalent:** [`JPopupMenu`](https://docs.oracle.com/javase/tutorial/uiswing/components/menu.html#popup)
 *
 * @param onDismissRequest Called when the menu should be dismissed, returns true if the dismissal was handled
 * @param horizontalAlignment The horizontal alignment of the menu relative to its anchor point
 * @param modifier Modifier to be applied to the menu container
 * @param style The visual styling configuration for the menu and its items
 * @param popupProperties Properties controlling the popup window behavior
 * @param content The menu content builder using [MenuScope]
 * @see javax.swing.JPopupMenu
 */
@Composable
public fun PopupMenu(
    onDismissRequest: (InputMode) -> Boolean,
    horizontalAlignment: Alignment.Horizontal,
    modifier: Modifier = Modifier,
    style: MenuStyle = JewelTheme.menuStyle,
    popupProperties: PopupProperties = PopupProperties(focusable = true),
    content: MenuScope.() -> Unit,
) {
    val density = LocalDensity.current

    val popupPositionProvider =
        AnchorVerticalMenuPositionProvider(
            contentOffset = style.metrics.offset,
            contentMargin = style.metrics.menuMargin,
            alignment = horizontalAlignment,
            density = density,
        )

    var focusManager: FocusManager? by remember { mutableStateOf(null) }
    var inputModeManager: InputModeManager? by remember { mutableStateOf(null) }
    val menuManager = remember(onDismissRequest) { MenuManager(onDismissRequest = onDismissRequest) }

    Popup(
        popupPositionProvider = popupPositionProvider,
        onDismissRequest = { onDismissRequest(InputMode.Touch) },
        properties = popupProperties,
        onPreviewKeyEvent = { false },
        onKeyEvent = {
            val currentFocusManager = checkNotNull(focusManager) { "FocusManager must not be null" }
            val currentInputModeManager = checkNotNull(inputModeManager) { "InputModeManager must not be null" }
            handlePopupMenuOnKeyEvent(it, currentFocusManager, currentInputModeManager, menuManager)
        },
    ) {
        focusManager = LocalFocusManager.current
        inputModeManager = LocalInputModeManager.current

        OverrideDarkMode(style.isDark) {
            CompositionLocalProvider(LocalMenuManager provides menuManager, LocalMenuStyle provides style) {
                MenuContent(modifier = modifier, content = content)
            }
        }
    }
}

@Composable
internal fun MenuContent(
    modifier: Modifier = Modifier,
    style: MenuStyle = JewelTheme.menuStyle,
    content: MenuScope.() -> Unit,
) {
    val items by remember(content) { derivedStateOf { content.asList() } }

    val selectableItems = remember { items.filterIsInstance<MenuSelectableItem>() }

    val anyItemHasIcon = remember { selectableItems.any { it.iconKey != null } }
    val anyItemHasKeybinding = remember { selectableItems.any { it.keybinding != null } }

    val localMenuManager = LocalMenuManager.current
    val scrollState = rememberScrollState()
    val colors = style.colors
    val menuShape = RoundedCornerShape(style.metrics.cornerSize)

    Box(
        modifier =
            modifier
                .shadow(
                    elevation = style.metrics.shadowSize,
                    shape = menuShape,
                    ambientColor = colors.shadow,
                    spotColor = colors.shadow,
                )
                .border(Stroke.Alignment.Inside, style.metrics.borderWidth, colors.border, menuShape)
                .background(colors.background, menuShape)
                .width(IntrinsicSize.Max)
                .onHover { localMenuManager.onHoveredChange(it) }
    ) {
        Column(Modifier.verticalScroll(scrollState).padding(style.metrics.contentPadding)) {
            items.forEach { ShowMenuItem(it, anyItemHasIcon, anyItemHasKeybinding) }
        }

        Box(modifier = Modifier.matchParentSize()) {
            VerticalScrollbar(
                rememberScrollbarAdapter(scrollState),
                modifier = Modifier.fillMaxHeight().align(Alignment.CenterEnd),
            )
        }
    }
}

@Composable
private fun ShowMenuItem(item: MenuItem, canShowIcon: Boolean = false, canShowKeybinding: Boolean = false) {
    when (item) {
        is MenuSelectableItem ->
            MenuItem(
                selected = item.isSelected,
                onClick = item.onClick,
                enabled = item.isEnabled,
                canShowIcon = canShowIcon,
                canShowKeybinding = canShowKeybinding,
                iconKey = item.iconKey,
                keybinding = item.keybinding,
                content = item.content,
            )

        is SubmenuItem ->
            MenuSubmenuItem(
                enabled = item.isEnabled,
                submenu = item.submenu,
                canShowIcon = canShowIcon,
                iconKey = item.iconKey,
                content = item.content,
            )

        else -> item.content()
    }
}

/**
 * Scope interface for building menu content using a DSL-style API.
 *
 * This interface provides methods for adding various types of menu items:
 * - Selectable items with optional icons and keybindings
 * - Submenus for nested menu structures
 * - Passive items for custom content
 * - Separators for visual grouping
 *
 * **Usage example:**
 *
 * ```kotlin
 * PopupMenu {
 *     selectableItem(selected = false, onClick = { /* handle click */ }) {
 *         Text("Menu Item")
 *     }
 *     separator()
 *     submenu(content = { Text("Submenu") }) {
 *         selectableItem(selected = true, onClick = { /* handle click */ }) {
 *             Text("Submenu Item")
 *         }
 *     }
 * }
 * ```
 */
public interface MenuScope {
    /**
     * Adds a selectable menu item with optional icon and keybinding.
     *
     * Creates a menu item that can be selected and clicked. The item supports an optional icon and keybinding display,
     * and can be enabled or disabled. When clicked, the provided onClick handler is called.
     *
     * @param selected Whether this item is currently selected
     * @param iconKey Optional icon key for displaying an icon before the content
     * @param keybinding Optional set of strings representing the keybinding (e.g., ["Ctrl", "C"])
     * @param onClick Called when the item is clicked
     * @param enabled Controls whether the item can be interacted with
     * @param content The content to be displayed in the menu item
     */
    public fun selectableItem(
        selected: Boolean,
        iconKey: IconKey? = null,
        keybinding: Set<String>? = null,
        onClick: () -> Unit,
        enabled: Boolean = true,
        content: @Composable () -> Unit,
    )

    /**
     * Adds a submenu item that opens a nested menu when hovered or clicked.
     *
     * Creates a menu item that displays a nested menu when the user interacts with it. The submenu can have its own
     * items and supports the same features as the parent menu. Submenus can be nested to create hierarchical menu
     * structures.
     *
     * @param enabled Controls whether the submenu can be interacted with
     * @param iconKey Optional icon key for displaying an icon before the content
     * @param submenu Builder for the submenu content using the same [MenuScope] DSL
     * @param content The content to be displayed in the submenu item
     */
    public fun submenu(
        enabled: Boolean = true,
        iconKey: IconKey? = null,
        submenu: MenuScope.() -> Unit,
        content: @Composable () -> Unit,
    )

    /**
     * Adds a non-interactive item with custom content to the menu.
     *
     * Creates a menu item that displays custom content but does not respond to user interaction. This is useful for
     * displaying informational content, headers, or other non-interactive elements within the menu.
     *
     * @param content The custom content to be displayed in the menu item
     */
    public fun passiveItem(content: @Composable () -> Unit)
}

/**
 * Adds a visual separator line between menu items.
 *
 * Creates a horizontal line that helps visually group related menu items. This is commonly used to separate different
 * sections of a menu, making it easier for users to understand the menu's structure.
 */
public fun MenuScope.separator() {
    passiveItem { MenuSeparator() }
}

public fun MenuScope.items(
    count: Int,
    isSelected: (Int) -> Boolean,
    onItemClick: (Int) -> Unit,
    content: @Composable (Int) -> Unit,
) {
    repeat(count) { selectableItem(isSelected(it), onClick = { onItemClick(it) }) { content(it) } }
}

public fun <T> MenuScope.items(
    items: List<T>,
    isSelected: (T) -> Boolean,
    onItemClick: (T) -> Unit,
    content: @Composable (T) -> Unit,
) {
    repeat(items.count()) {
        selectableItem(isSelected(items[it]), onClick = { onItemClick(items[it]) }) { content(items[it]) }
    }
}

private fun (MenuScope.() -> Unit).asList() = buildList {
    this@asList(
        object : MenuScope {
            override fun selectableItem(
                selected: Boolean,
                iconKey: IconKey?,
                keybinding: Set<String>?,
                onClick: () -> Unit,
                enabled: Boolean,
                content: @Composable () -> Unit,
            ) {
                add(
                    MenuSelectableItem(
                        isSelected = selected,
                        isEnabled = enabled,
                        iconKey = iconKey,
                        keybinding = keybinding,
                        onClick = onClick,
                        content = content,
                    )
                )
            }

            override fun passiveItem(content: @Composable () -> Unit) {
                add(MenuPassiveItem(content))
            }

            override fun submenu(
                enabled: Boolean,
                iconKey: IconKey?,
                submenu: MenuScope.() -> Unit,
                content: @Composable () -> Unit,
            ) {
                add(SubmenuItem(enabled, iconKey, submenu, content))
            }
        }
    )
}

private interface MenuItem {
    val content: @Composable () -> Unit
}

private data class MenuSelectableItem(
    val isSelected: Boolean,
    val isEnabled: Boolean,
    val iconKey: IconKey?,
    val keybinding: Set<String>?,
    val onClick: () -> Unit = {},
    override val content: @Composable () -> Unit,
) : MenuItem

private data class MenuPassiveItem(override val content: @Composable () -> Unit) : MenuItem

private data class SubmenuItem(
    val isEnabled: Boolean = true,
    val iconKey: IconKey?,
    val submenu: MenuScope.() -> Unit,
    override val content: @Composable () -> Unit,
) : MenuItem

@Composable
public fun MenuSeparator(
    modifier: Modifier = Modifier,
    metrics: MenuItemMetrics = JewelTheme.menuStyle.metrics.itemMetrics,
    colors: MenuItemColors = JewelTheme.menuStyle.colors.itemColors,
) {
    Box(modifier.height(metrics.separatorHeight)) {
        Divider(
            orientation = Orientation.Horizontal,
            modifier = Modifier.fillMaxWidth().padding(metrics.separatorPadding),
            color = colors.separator,
            thickness = metrics.separatorThickness,
        )
    }
}

@Composable
internal fun MenuItem(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconKey: IconKey?,
    keybinding: Set<String>?,
    canShowIcon: Boolean,
    canShowKeybinding: Boolean,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: MenuStyle = JewelTheme.menuStyle,
    content: @Composable () -> Unit,
) {
    var itemState by
        remember(interactionSource) { mutableStateOf(MenuItemState.of(selected = selected, enabled = enabled)) }

    remember(enabled, selected) { itemState = itemState.copy(selected = selected, enabled = enabled) }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> itemState = itemState.copy(pressed = true)
                is PressInteraction.Cancel,
                is PressInteraction.Release -> itemState = itemState.copy(pressed = false)

                is HoverInteraction.Enter -> {
                    itemState = itemState.copy(hovered = true)
                    focusRequester.requestFocus()
                }

                is HoverInteraction.Exit -> itemState = itemState.copy(hovered = false)
                is FocusInteraction.Focus -> itemState = itemState.copy(focused = true)
                is FocusInteraction.Unfocus -> itemState = itemState.copy(focused = false)
            }
        }
    }

    val menuManager = LocalMenuManager.current
    val localInputModeManager = LocalInputModeManager.current

    Box(
        modifier =
            modifier
                .focusRequester(focusRequester)
                .selectable(
                    selected = selected,
                    onClick = {
                        onClick()
                        menuManager.closeAll(localInputModeManager.inputMode, true)
                    },
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                )
                .fillMaxWidth()
    ) {
        DisposableEffect(Unit) {
            if (selected) {
                focusRequester.requestFocus()
            }

            onDispose {}
        }

        val itemColors = style.colors.itemColors
        val itemMetrics = style.metrics.itemMetrics

        val updatedTextStyle = LocalTextStyle.current.copy(color = itemColors.contentFor(itemState).value)
        CompositionLocalProvider(
            LocalContentColor provides itemColors.contentFor(itemState).value,
            LocalTextStyle provides updatedTextStyle,
        ) {
            val backgroundColor by itemColors.backgroundFor(itemState)

            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .defaultMinSize(minHeight = itemMetrics.minHeight)
                        .drawItemBackground(itemMetrics, backgroundColor)
                        .padding(itemMetrics.contentPadding),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (canShowIcon) {
                    val iconModifier = Modifier.size(style.metrics.itemMetrics.iconSize)
                    if (iconKey != null) {
                        Icon(key = iconKey, contentDescription = null, modifier = iconModifier)
                    } else {
                        Box(modifier = iconModifier)
                    }
                }

                Box(modifier = Modifier.weight(1f, true)) { content() }

                if (canShowKeybinding) {
                    val keybindingText =
                        remember(keybinding) {
                            if (hostOs.isMacOS) {
                                keybinding?.joinToString(" ") { it }.orEmpty()
                            } else {
                                keybinding?.joinToString(" + ") { it }.orEmpty()
                            }
                        }
                    Text(
                        modifier = Modifier.padding(style.metrics.itemMetrics.keybindingsPadding),
                        text = keybindingText,
                        color = itemColors.keybindingTintFor(itemState).value,
                    )
                }
            }
        }
    }
}

@Composable
public fun MenuSubmenuItem(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    canShowIcon: Boolean,
    iconKey: IconKey?,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: MenuStyle = JewelTheme.menuStyle,
    submenu: MenuScope.() -> Unit,
    content: @Composable () -> Unit,
) {
    var itemState by
        remember(interactionSource) { mutableStateOf(MenuItemState.of(selected = false, enabled = enabled)) }

    remember(enabled) { itemState = itemState.copy(selected = false, enabled = enabled) }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> itemState = itemState.copy(pressed = true)
                is PressInteraction.Cancel,
                is PressInteraction.Release -> itemState = itemState.copy(pressed = false)

                is HoverInteraction.Enter -> {
                    itemState = itemState.copy(hovered = true, selected = true)
                    focusRequester.requestFocus()
                }

                is HoverInteraction.Exit -> itemState = itemState.copy(hovered = false)
                is FocusInteraction.Focus -> itemState = itemState.copy(focused = true)
                is FocusInteraction.Unfocus -> itemState = itemState.copy(focused = false)
            }
        }
    }

    val itemColors = style.colors.itemColors
    val menuMetrics = style.metrics

    val backgroundColor by itemColors.backgroundFor(itemState)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .drawItemBackground(menuMetrics.itemMetrics, backgroundColor)
                .focusRequester(focusRequester)
                .clickable(
                    onClick = { itemState = itemState.copy(selected = !itemState.isSelected) },
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                )
                .onKeyEvent {
                    if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionRight) {
                        itemState = itemState.copy(selected = true)
                        true
                    } else {
                        false
                    }
                }
    ) {
        CompositionLocalProvider(LocalContentColor provides itemColors.contentFor(itemState).value) {
            Row(
                Modifier.fillMaxWidth().padding(menuMetrics.itemMetrics.contentPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (canShowIcon) {
                    if (iconKey != null) {
                        Icon(key = iconKey, contentDescription = null)
                    } else {
                        Box(Modifier.size(style.metrics.itemMetrics.iconSize))
                    }
                }

                Box(Modifier.weight(1f)) { content() }

                Icon(
                    key = style.icons.submenuChevron,
                    tint = itemColors.iconTintFor(itemState).value,
                    contentDescription = null,
                    modifier = Modifier.size(style.metrics.itemMetrics.iconSize),
                    hint = Stateful(itemState),
                )
            }
        }

        if (itemState.isSelected) {
            Submenu(
                onDismissRequest = {
                    if (it == InputMode.Touch && itemState.isHovered) {
                        false
                    } else {
                        itemState = itemState.copy(selected = false)
                        true
                    }
                },
                style = style,
                content = submenu,
            )
        }
    }
}

private fun Modifier.drawItemBackground(itemMetrics: MenuItemMetrics, backgroundColor: Color) = drawBehind {
    val cornerSizePx = itemMetrics.selectionCornerSize.toPx(size, density = this)
    val cornerRadius = CornerRadius(cornerSizePx, cornerSizePx)

    val outerPadding = itemMetrics.outerPadding
    val offset =
        Offset(
            x = outerPadding.calculateLeftPadding(layoutDirection).toPx(),
            y = outerPadding.calculateTopPadding().toPx(),
        )
    drawRoundRect(
        color = backgroundColor,
        cornerRadius = cornerRadius,
        topLeft = offset,
        size = size.subtract(outerPadding, density = this, layoutDirection),
    )
}

private fun Size.subtract(paddingValues: PaddingValues, density: Density, layoutDirection: LayoutDirection): Size =
    with(density) {
        Size(
            width -
                paddingValues.calculateLeftPadding(layoutDirection).toPx() -
                paddingValues.calculateRightPadding(layoutDirection).toPx(),
            height - paddingValues.calculateTopPadding().toPx() - paddingValues.calculateBottomPadding().toPx(),
        )
    }

@Composable
internal fun Submenu(
    onDismissRequest: (InputMode) -> Boolean,
    modifier: Modifier = Modifier,
    style: MenuStyle = JewelTheme.menuStyle,
    content: MenuScope.() -> Unit,
) {
    val density = LocalDensity.current

    val popupPositionProvider =
        AnchorHorizontalMenuPositionProvider(
            contentOffset = style.metrics.submenuMetrics.offset,
            contentMargin = style.metrics.menuMargin,
            alignment = Alignment.Top,
            density = density,
        )

    var focusManager: FocusManager? by remember { mutableStateOf(null) }
    var inputModeManager: InputModeManager? by remember { mutableStateOf(null) }
    val parentMenuManager = LocalMenuManager.current
    val menuManager =
        remember(parentMenuManager, onDismissRequest) { parentMenuManager.submenuManager(onDismissRequest) }

    Popup(
        popupPositionProvider = popupPositionProvider,
        onDismissRequest = { menuManager.closeAll(InputMode.Touch, false) },
        properties = PopupProperties(focusable = true),
        onPreviewKeyEvent = { false },
        onKeyEvent = {
            val currentFocusManager = checkNotNull(focusManager) { "FocusManager must not be null" }
            val currentInputModeManager = checkNotNull(inputModeManager) { "InputModeManager must not be null" }
            handlePopupMenuOnKeyEvent(it, currentFocusManager, currentInputModeManager, menuManager)
        },
    ) {
        focusManager = LocalFocusManager.current
        inputModeManager = LocalInputModeManager.current

        CompositionLocalProvider(LocalMenuManager provides menuManager) {
            MenuContent(modifier = modifier, content = content)
        }
    }
}

/**
 * State holder for menu items that tracks various interaction states.
 *
 * This class maintains the state of a menu item, including selection, enabled/disabled state, focus, hover, and press
 * states. It implements both [SelectableComponentState] and [FocusableComponentState] for consistent behavior with
 * other interactive components.
 *
 * The state is stored efficiently using a bit-masked value, where each bit represents a different state flag.
 *
 * @property state The raw bit-masked state value
 * @see SelectableComponentState
 * @see FocusableComponentState
 */
@Immutable
@JvmInline
public value class MenuItemState(public val state: ULong) : SelectableComponentState, FocusableComponentState {
    override val isActive: Boolean
        get() = state and Selected != 0UL

    override val isSelected: Boolean
        get() = state and Selected != 0UL

    override val isEnabled: Boolean
        get() = state and Enabled != 0UL

    override val isFocused: Boolean
        get() = state and Focused != 0UL

    override val isHovered: Boolean
        get() = state and Hovered != 0UL

    override val isPressed: Boolean
        get() = state and Pressed != 0UL

    public fun copy(
        selected: Boolean = isSelected,
        enabled: Boolean = isEnabled,
        focused: Boolean = isFocused,
        hovered: Boolean = isHovered,
        pressed: Boolean = isPressed,
        active: Boolean = isActive,
    ): MenuItemState =
        of(
            selected = selected,
            enabled = enabled,
            focused = focused,
            hovered = hovered,
            pressed = pressed,
            active = active,
        )

    override fun toString(): String =
        "MenuItemState(state=$state, isSelected=$isSelected, isEnabled=$isEnabled, isFocused=$isFocused, " +
            "isHovered=$isHovered, isPressed=$isPressed, isActive=$isActive)"

    public companion object {
        public fun of(
            selected: Boolean,
            enabled: Boolean,
            focused: Boolean = false,
            hovered: Boolean = false,
            pressed: Boolean = false,
            active: Boolean = false,
        ): MenuItemState {
            var state = 0UL
            if (selected) state = state or Selected
            if (enabled) state = state or Enabled
            if (focused) state = state or Focused
            if (hovered) state = state or Hovered
            if (pressed) state = state or Pressed
            if (active) state = state or Active
            return MenuItemState(state)
        }
    }
}
