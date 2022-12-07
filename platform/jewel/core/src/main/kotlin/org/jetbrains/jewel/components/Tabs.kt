package org.jetbrains.jewel.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.semantics.Role
import org.jetbrains.jewel.Orientation
import org.jetbrains.jewel.components.state.TabState
import org.jetbrains.jewel.shape
import org.jetbrains.jewel.styles.LocalTabStyle
import org.jetbrains.jewel.styles.LocalTextStyle
import org.jetbrains.jewel.styles.Styles
import org.jetbrains.jewel.styles.TabAppearance
import org.jetbrains.jewel.styles.TabStyle
import org.jetbrains.jewel.styles.withTextStyle

@Composable
fun <T : Any> TabRow(
    tabState: TabContainerState<T>,
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Bottom,
    tabStyle: TabStyle = LocalTabStyle.current,
    content: @Composable TabScope<T>.() -> Unit
) {
    // TODO: refactor to support tab indicator (adornment) animation
    // Basic idea is to use onGloballyPositioned to track layout of tabs, and then create separate box
    // placed exactly at active box position/size and attach adornment to it, then animate it's position and size

    Row(
        modifier = Modifier.selectableGroup()
            .height(IntrinsicSize.Max)
            .then(modifier),
        verticalAlignment = verticalAlignment,
        horizontalArrangement = horizontalArrangement
    ) {
        val scope = TabRowScope(tabState, tabStyle, Orientation.Horizontal, this@Row)
        scope.content()
    }
}

@Composable
fun <T : Any> TabColumn(
    tabState: TabContainerState<T>,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    tabStyle: TabStyle = LocalTabStyle.current,
    content: @Composable TabScope<T>.() -> Unit
) {
    Column(
        modifier = Modifier.selectableGroup().width(IntrinsicSize.Max).then(modifier),
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment
    ) {
        val scope = TabColumnScope(tabState, tabStyle, Orientation.Vertical, this@Column)
        scope.content()
    }
}

interface TabScope<T : Any> {

    val state: TabContainerState<T>
    val style: TabStyle
    val orientation: Orientation
}

@Immutable
internal class TabRowScope<T : Any>(
    override val state: TabContainerState<T>,
    override val style: TabStyle,
    override val orientation: Orientation,
    rowScope: RowScope
) : TabScope<T>,
    RowScope by rowScope

@Immutable
internal class TabColumnScope<T : Any>(
    override val state: TabContainerState<T>,
    override val style: TabStyle,
    override val orientation: Orientation,
    columnScope: ColumnScope
) : TabScope<T>,
    ColumnScope by columnScope

@Composable
fun <T : Any> TabScope<T>.Tab(
    key: T?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val selected = state.selectedKey == key
    var isHovered by remember { mutableStateOf(false) }

    val tabState = when {
        !enabled -> TabState.Disabled
        isHovered and selected -> TabState.SelectedAndHovered
        selected -> TabState.Selected
        else -> if (isHovered) TabState.Hovered else TabState.Normal
    }
    val appearance = style.appearance(tabState, orientation)

    val sizeModifier = when (orientation) {
        Orientation.Vertical -> Modifier.fillMaxWidth()
        Orientation.Horizontal -> Modifier.fillMaxHeight()
    }

    val shapeModifier = if (appearance.shapeStroke != null || appearance.backgroundColor != Color.Unspecified) {
        Modifier.shape(appearance.shape, appearance.shapeStroke, appearance.backgroundColor)
    } else {
        Modifier
    }
    val adornmentModifier = if (appearance.adornmentStroke != null && appearance.adornmentShape != null) {
        Modifier.shape(appearance.adornmentShape, appearance.adornmentStroke)
    } else {
        Modifier
    }

    @OptIn(ExperimentalComposeUiApi::class)
    Box(
        modifier
            .clickable(
                onClick = { state.select(key) },
                enabled = enabled,
                role = Role.Tab
                /*
                                interactionSource = interactionSource,
                                indication = null
                */
            ).onPointerEvent(
                PointerEventType.Move
            ) {
            }
            .onPointerEvent(PointerEventType.Enter) {
                isHovered = true
            }
            .onPointerEvent(PointerEventType.Exit) {
                isHovered = false
            }
            .then(sizeModifier)
            .then(shapeModifier)
            .then(adornmentModifier)
            .clip(appearance.shape),
        propagateMinConstraints = true
    ) {
        TabContent(appearance, content)
    }
}

@Composable
private fun TabContent(appearance: TabAppearance, content: @Composable (RowScope.() -> Unit)) {
    Styles.withTextStyle(LocalTextStyle.current.merge(appearance.textStyle)) {
        Row(
            Modifier
                .defaultMinSize(minWidth = appearance.minWidth, minHeight = appearance.minHeight)
                // .indication(interactionSource, rememberRipple())
                .padding(appearance.contentPadding),
            horizontalArrangement = appearance.contentArrangement,
            verticalAlignment = appearance.contentAlignment,
            content = content
        )
    }
}

interface TabContainerState<T : Any> {

    fun select(key: T?)
    val selectedKey: T?
}

@Composable
fun <T : Any> rememberTabContainerState(initialKey: T? = null): TabContainerState<T> =
    rememberSaveable(saver = DefaultTabContainerState.saver()) { DefaultTabContainerState(initialKey = initialKey) }

@Stable
class DefaultTabContainerState<T : Any>(initialKey: T?) : TabContainerState<T> {

    override fun select(key: T?) {
        selectedKey = key
    }

    override var selectedKey: T? by mutableStateOf(initialKey, structuralEqualityPolicy())
        private set

    companion object {

        fun <T : Any> saver(): Saver<DefaultTabContainerState<T>, *> = Saver(
            save = { it.selectedKey },
            restore = { DefaultTabContainerState(it) }
        )
    }
}
