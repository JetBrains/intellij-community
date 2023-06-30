package org.jetbrains.jewel.themes.intui.standalone

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.MenuDefaults

@Suppress("VariableNaming")
abstract class IntUiMenuDefaults : MenuDefaults {

    private val MenuShape = RoundedCornerShape(8.dp)

    private val MenuItemShape = RoundedCornerShape(4.dp)

    private val MenuMargin = PaddingValues(8.dp)

    private val MenuOffset = DpOffset(0.dp, 2.dp)

    private val SubmenuOffset = DpOffset(2.dp, (-8).dp)

    private val MenuPadding = PaddingValues(12.dp)

    private val MenuContentPadding = PaddingValues(vertical = 8.dp)

    private val MenuItemPadding = PaddingValues(horizontal = 12.dp)

    private val MenuItemContentPadding = PaddingValues(8.dp, 4.dp)

    private val MenuSubmenuItemContentPadding = PaddingValues(start = 8.dp, top = 4.dp, bottom = 4.dp)

    private val MenuSeparatorPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)

    private val ShadowSize = 12.dp

    @Composable
    override fun menuShape(): Shape = MenuShape

    @Composable
    override fun menuMargin(): PaddingValues = MenuMargin

    @Composable
    override fun menuOffset(): DpOffset = MenuOffset

    @Composable
    override fun submenuOffset(): DpOffset = SubmenuOffset

    @Composable
    override fun menuAlignment(): Alignment.Horizontal = Alignment.CenterHorizontally

    @Composable
    override fun menuPadding(): PaddingValues = MenuPadding

    @Composable
    override fun submenuChevronPainter(): Painter = painterResource("intui/chevronRight.svg")

    @Composable
    override fun menuItemPadding(): PaddingValues = MenuItemPadding

    @Composable
    override fun menuItemShape(): Shape = MenuItemShape

    @Composable
    override fun menuContentPadding(): PaddingValues = MenuContentPadding

    @Composable
    override fun menuSubmenuItemContentPadding(): PaddingValues = MenuSubmenuItemContentPadding

    @Composable
    override fun menuItemContentPadding(): PaddingValues = MenuItemContentPadding

    @Composable
    override fun menuShadowSize(): Dp = ShadowSize

    @Composable
    override fun menuSeparatorPadding(): PaddingValues = MenuSeparatorPadding
}
