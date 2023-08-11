package org.jetbrains.jewel

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import org.jetbrains.jewel.CommonStateBitMask.Active
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.border
import org.jetbrains.jewel.foundation.onHover
import org.jetbrains.jewel.styling.MenuStyle

@Composable
internal fun MenuContent(
    modifier: Modifier = Modifier,
    style: MenuStyle = IntelliJTheme.menuStyle,
    content: MenuScope.() -> Unit,
) {
    val items by remember(content) {
        derivedStateOf {
            content.asList()
        }
    }

    val localMenuManager = LocalMenuManager.current
    val scrollState = rememberScrollState()
    val colors = style.colors
    val menuShape = RoundedCornerShape(style.metrics.cornerSize)

    Box(
        modifier = modifier.shadow(
            elevation = style.metrics.shadowSize,
            shape = menuShape,
            ambientColor = colors.shadow,
            spotColor = colors.shadow
        )
            .border(Stroke.Alignment.Center, style.metrics.borderWidth, colors.border, menuShape)
            .background(colors.background, menuShape)
            .width(IntrinsicSize.Max)
            .onHover {
                localMenuManager.onHoveredChange(it)
            }
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .padding(style.metrics.contentPadding)
        ) {
            items.forEach {
                when (it) {
                    is MenuSelectableItem -> {
                        MenuSelectableItem(
                            selected = it.isSelected,
                            onClick = it.onClick,
                            enabled = it.isEnabled,
                            content = it.content
                        )
                    }

                    is SubmenuItem -> {
                        MenuSubmenuItem(
                            enabled = it.isEnabled,
                            submenu = it.submenu,
                            content = it.content
                        )
                    }

                    else -> {
                        it.content()
                    }
                }
            }
        }
        Box(modifier = Modifier.matchParentSize()) {
            VerticalScrollbar(
                rememberScrollbarAdapter(scrollState),
                modifier = Modifier.fillMaxHeight().align(Alignment.CenterEnd)
            )
        }
    }
}

interface MenuScope {

    fun selectableItem(
        selected: Boolean,
        onClick: () -> Unit,
        enabled: Boolean = true,
        content: @Composable () -> Unit,
    )

    fun submenu(
        enabled: Boolean = true,
        submenu: MenuScope.() -> Unit,
        content: @Composable () -> Unit,
    )

    fun passiveItem(content: @Composable () -> Unit)
}

fun MenuScope.divider() {
    passiveItem {
        MenuSeparator()
    }
}

fun MenuScope.items(
    count: Int,
    isSelected: (Int) -> Boolean,
    onItemClick: (Int) -> Unit,
    content: @Composable (Int) -> Unit,
) {
    repeat(count) {
        selectableItem(isSelected(it), onClick = { onItemClick(it) }) { content(it) }
    }
}

fun <T> MenuScope.items(
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
                onClick: () -> Unit,
                enabled: Boolean,
                content: @Composable () -> Unit,
            ) {
                add(MenuSelectableItem(selected, enabled, onClick, content))
            }

            override fun passiveItem(content: @Composable () -> Unit) {
                add(MenuPassiveItem(content))
            }

            override fun submenu(enabled: Boolean, submenu: MenuScope.() -> Unit, content: @Composable () -> Unit) {
                add(SubmenuItem(enabled, submenu, content))
            }
        }
    )
}

private interface MenuItem {

    val content: @Composable () -> Unit
}

private data class MenuSelectableItem(
    val isSelected: Boolean,
    val isEnabled: Boolean = true,
    val onClick: () -> Unit = {},
    override val content: @Composable () -> Unit,
) : MenuItem

private data class MenuPassiveItem(
    override val content: @Composable () -> Unit,
) : MenuItem

private data class SubmenuItem(
    val isEnabled: Boolean = true,
    val submenu: MenuScope.() -> Unit,
    override val content: @Composable () -> Unit,
) : MenuItem

@Composable
fun MenuSeparator(
    modifier: Modifier = Modifier,
    style: MenuStyle = IntelliJTheme.menuStyle,
) {
    Divider(
        modifier = modifier.padding(style.metrics.itemMetrics.separatorPadding),
        orientation = Orientation.Horizontal,
        color = style.colors.itemColors.separator
    )
}

@Composable
fun MenuSelectableItem(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: MenuStyle = IntelliJTheme.menuStyle,
    content: @Composable () -> Unit,
) {
    var itemState by remember(interactionSource) {
        mutableStateOf(MenuItemState.of(selected = selected, enabled = enabled))
    }

    remember(enabled, selected) {
        itemState = itemState.copy(selected = selected, enabled = enabled)
    }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> itemState = itemState.copy(pressed = true)
                is PressInteraction.Cancel, is PressInteraction.Release -> itemState = itemState.copy(pressed = false)
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
        modifier = modifier
            .focusRequester(focusRequester)
            .selectable(
                selected = selected,
                onClick = {
                    onClick()
                    menuManager.closeAll(localInputModeManager.inputMode, true)
                },
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null
            )
            .fillMaxWidth()
    ) {
        DisposableEffect(Unit) {
            if (selected) {
                focusRequester.requestFocus()
            }

            onDispose { }
        }

        val colors = style.colors.itemColors
        val metrics = style.metrics
        val shape = RoundedCornerShape(metrics.itemMetrics.cornerSize)

        CompositionLocalProvider(
            LocalContentColor provides colors.contentFor(itemState).value
        ) {
            Row(
                Modifier
                    .padding(metrics.itemMetrics.padding)
                    .background(colors.backgroundFor(itemState).value, shape)
                    .fillMaxWidth()
                    .padding(metrics.itemMetrics.contentPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                content()
            }
        }
    }
}

@Composable
fun MenuSubmenuItem(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: MenuStyle = IntelliJTheme.menuStyle,
    submenu: MenuScope.() -> Unit,
    content: @Composable () -> Unit,
) {
    var itemState by remember(interactionSource) {
        mutableStateOf(MenuItemState.of(selected = false, enabled = enabled))
    }

    remember(enabled) {
        itemState = itemState.copy(selected = false, enabled = enabled)
    }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> itemState = itemState.copy(pressed = true)
                is PressInteraction.Cancel, is PressInteraction.Release -> itemState = itemState.copy(pressed = false)
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

    Box(
        modifier = modifier
            .focusRequester(focusRequester)
            .clickable(
                onClick = {
                    itemState = itemState.copy(selected = !itemState.isSelected)
                },
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null
            )
            .onKeyEvent {
                if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionRight) {
                    itemState = itemState.copy(selected = true)
                    true
                } else {
                    false
                }
            }
            .fillMaxWidth()
    ) {
        val colors = style.colors.itemColors
        val metrics = style.metrics
        val shape = RoundedCornerShape(metrics.itemMetrics.cornerSize)

        CompositionLocalProvider(
            LocalContentColor provides colors.contentFor(itemState).value
        ) {
            Row(
                Modifier
                    .padding(metrics.itemMetrics.padding)
                    .background(colors.backgroundFor(itemState).value, shape)
                    .fillMaxWidth()
                    .padding(metrics.submenuMetrics.itemPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f)) {
                    content()
                }

                Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(style.icons.submenuChevron),
                        tint = colors.iconTintFor(itemState).value
                    )
                }
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
                content = submenu
            )
        }
    }
}

@Composable
internal fun Submenu(
    onDismissRequest: (InputMode) -> Boolean,
    modifier: Modifier = Modifier,
    style: MenuStyle = IntelliJTheme.menuStyle,
    content: MenuScope.() -> Unit,
) {
    val density = LocalDensity.current

    val popupPositionProvider = AnchorHorizontalMenuPositionProvider(
        contentOffset = style.metrics.submenuMetrics.offset,
        contentMargin = style.metrics.margin,
        alignment = Alignment.Top,
        density = density
    )

    var focusManager: FocusManager? by mutableStateOf(null)
    var inputModeManager: InputModeManager? by mutableStateOf(null)
    val parentMenuManager = LocalMenuManager.current
    val menuManager = remember(parentMenuManager, onDismissRequest) {
        parentMenuManager.submenuManager(onDismissRequest)
    }

    Popup(
        focusable = true,
        onDismissRequest = {
            menuManager.closeAll(InputMode.Touch, false)
        },
        popupPositionProvider = popupPositionProvider,
        onKeyEvent = {
            val currentFocusManager = checkNotNull(focusManager) { "FocusManager must not be null" }
            val currentInputModeManager = checkNotNull(inputModeManager) { "InputModeManager must not be null" }
            handlePopupMenuOnKeyEvent(it, currentFocusManager, currentInputModeManager, menuManager)
        }
    ) {
        focusManager = LocalFocusManager.current
        inputModeManager = LocalInputModeManager.current

        CompositionLocalProvider(LocalMenuManager provides menuManager) {
            MenuContent(
                modifier = modifier,
                content = content
            )
        }
    }
}

@Immutable
@JvmInline
value class MenuItemState(val state: ULong) : SelectableComponentState {

    @Stable
    override val isActive: Boolean
        get() = state and Selected != 0UL

    @Stable
    override val isSelected: Boolean
        get() = state and Selected != 0UL

    @Stable
    override val isEnabled: Boolean
        get() = state and Enabled != 0UL

    @Stable
    override val isFocused: Boolean
        get() = state and Focused != 0UL

    @Stable
    override val isHovered: Boolean
        get() = state and Hovered != 0UL

    @Stable
    override val isPressed: Boolean
        get() = state and Pressed != 0UL

    fun copy(
        selected: Boolean = isSelected,
        enabled: Boolean = isEnabled,
        focused: Boolean = isFocused,
        hovered: Boolean = isHovered,
        pressed: Boolean = isPressed,
        active: Boolean = isActive,
    ): MenuItemState =
        of(selected, enabled, focused, hovered, pressed, active)

    override fun toString() =
        "MenuItemState(state=$state, isSelected=$isSelected, isEnabled=$isEnabled, isFocused=$isFocused, " +
            "isHovered=$isHovered, isPressed=$isPressed, isActive=$isActive)"

    companion object {

        private val Enabled = 1UL shl 0
        private val Focused = 1UL shl 1
        private val Hovered = 1UL shl 2
        private val Pressed = 1UL shl 3
        private val Selected = 1UL shl 4

        fun of(
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

class MenuManager(
    val onDismissRequest: (InputMode) -> Boolean,
    private val parentMenuManager: MenuManager? = null,
) {

    private var isHovered: Boolean = false

    /**
     * Called when the hovered state of the menu changes.
     * This is used to abort parent menu closing in unforced mode
     * when submenu closed by click parent menu's item.
     *
     * @param hovered true if the menu is hovered, false otherwise.
     */
    internal fun onHoveredChange(hovered: Boolean) {
        isHovered = hovered
    }

    /**
     * Close all menus in the hierarchy.
     *
     * @param mode the input mode, menus close by pointer or keyboard event.
     * @param force true to force close all menus ignore parent hover state, false otherwise.
     */
    fun closeAll(mode: InputMode, force: Boolean) {
        // We ignore the pointer event if the menu is hovered in unforced mode.
        if (!force && mode == InputMode.Touch && isHovered) return

        if (onDismissRequest(mode)) {
            parentMenuManager?.closeAll(mode, force)
        }
    }

    fun close(mode: InputMode) = onDismissRequest(mode)

    fun isRootMenu(): Boolean = parentMenuManager == null

    fun isSubmenu(): Boolean = parentMenuManager != null

    fun submenuManager(onDismissRequest: (InputMode) -> Boolean) =
        MenuManager(onDismissRequest = onDismissRequest, parentMenuManager = this)
}

val LocalMenuManager = staticCompositionLocalOf<MenuManager> {
    error("No MenuManager provided")
}
