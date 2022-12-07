package org.jetbrains.jewel.themes.expui.standalone.theme

import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.themes.expui.standalone.control.ActionButtonColors
import org.jetbrains.jewel.themes.expui.standalone.control.ButtonColors
import org.jetbrains.jewel.themes.expui.standalone.control.CheckBoxColors
import org.jetbrains.jewel.themes.expui.standalone.control.ComboBoxColors
import org.jetbrains.jewel.themes.expui.standalone.control.ContextMenuColors
import org.jetbrains.jewel.themes.expui.standalone.control.DropdownMenuColors
import org.jetbrains.jewel.themes.expui.standalone.control.JbContextMenuRepresentation
import org.jetbrains.jewel.themes.expui.standalone.control.LinkColors
import org.jetbrains.jewel.themes.expui.standalone.control.LocalActionButtonColors
import org.jetbrains.jewel.themes.expui.standalone.control.LocalCheckBoxColors
import org.jetbrains.jewel.themes.expui.standalone.control.LocalCloseableTabColors
import org.jetbrains.jewel.themes.expui.standalone.control.LocalComboBoxColors
import org.jetbrains.jewel.themes.expui.standalone.control.LocalContextMenuColors
import org.jetbrains.jewel.themes.expui.standalone.control.LocalDropdownMenuColors
import org.jetbrains.jewel.themes.expui.standalone.control.LocalLinkColors
import org.jetbrains.jewel.themes.expui.standalone.control.LocalOutlineButtonColors
import org.jetbrains.jewel.themes.expui.standalone.control.LocalPrimaryButtonColors
import org.jetbrains.jewel.themes.expui.standalone.control.LocalProgressBarColors
import org.jetbrains.jewel.themes.expui.standalone.control.LocalRadioButtonColors
import org.jetbrains.jewel.themes.expui.standalone.control.LocalSegmentedButtonColors
import org.jetbrains.jewel.themes.expui.standalone.control.LocalTabColors
import org.jetbrains.jewel.themes.expui.standalone.control.LocalTextFieldColors
import org.jetbrains.jewel.themes.expui.standalone.control.LocalToolBarActionButtonColors
import org.jetbrains.jewel.themes.expui.standalone.control.LocalToolTipColors
import org.jetbrains.jewel.themes.expui.standalone.control.MainToolBarColors
import org.jetbrains.jewel.themes.expui.standalone.control.ProgressBarColors
import org.jetbrains.jewel.themes.expui.standalone.control.RadioButtonColors
import org.jetbrains.jewel.themes.expui.standalone.control.SegmentedButtonColors
import org.jetbrains.jewel.themes.expui.standalone.control.TabColors
import org.jetbrains.jewel.themes.expui.standalone.control.TextFieldColors
import org.jetbrains.jewel.themes.expui.standalone.control.ToolBarActionButtonColors
import org.jetbrains.jewel.themes.expui.standalone.control.ToolTipColors
import org.jetbrains.jewel.themes.expui.standalone.style.AreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalDefaultBoldTextStyle
import org.jetbrains.jewel.themes.expui.standalone.style.LocalDefaultTextStyle
import org.jetbrains.jewel.themes.expui.standalone.style.LocalDisabledAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalErrorAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalErrorInactiveAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalFocusAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalH0TextStyle
import org.jetbrains.jewel.themes.expui.standalone.style.LocalH1TextStyle
import org.jetbrains.jewel.themes.expui.standalone.style.LocalH2BoldTextStyle
import org.jetbrains.jewel.themes.expui.standalone.style.LocalH2TextStyle
import org.jetbrains.jewel.themes.expui.standalone.style.LocalH3BoldTextStyle
import org.jetbrains.jewel.themes.expui.standalone.style.LocalH3TextStyle
import org.jetbrains.jewel.themes.expui.standalone.style.LocalHoverAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalInactiveAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalMainToolBarColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalMediumBoldTextStyle
import org.jetbrains.jewel.themes.expui.standalone.style.LocalMediumTextStyle
import org.jetbrains.jewel.themes.expui.standalone.style.LocalParagraphTextStyle
import org.jetbrains.jewel.themes.expui.standalone.style.LocalPressedAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalSelectionAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalSelectionInactiveAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalSmallTextStyle
import org.jetbrains.jewel.themes.expui.standalone.style.LocalWindowsCloseWindowButtonColors

@Suppress("LargeClass")
object DarkTheme : Theme {

    val Grey1 = Color(0xFF1E1F22)
    val Grey2 = Color(0xFF2B2D30)
    val Grey3 = Color(0xFF393B40)
    val Grey4 = Color(0xFF43454A)
    val Grey5 = Color(0xFF4E5157)
    val Grey6 = Color(0xFF5A5D63)
    val Grey7 = Color(0xFF6F737A)
    val Grey8 = Color(0xFF868A91)
    val Grey9 = Color(0xFF9DA0A8)
    val Grey10 = Color(0xFFB4B8BF)
    val Grey11 = Color(0xFFCED0D6)
    val Grey12 = Color(0xFFDFE1E5)
    val Grey13 = Color(0xFFF0F1F2)
    val Grey14 = Color(0xFFFFFFFF)

    val Blue1 = Color(0xFF25324D)
    val Blue2 = Color(0xFF2E436E)
    val Blue3 = Color(0xFF35538F)
    val Blue4 = Color(0xFF375FAD)
    val Blue5 = Color(0xFF366ACE)
    val Blue6 = Color(0xFF3573F0)
    val Blue7 = Color(0xFF467FF2)
    val Blue8 = Color(0xFF548AF7)
    val Blue9 = Color(0xFF6B9BFA)
    val Blue10 = Color(0xFF83ACFC)
    val Blue11 = Color(0xFF99BBFF)

    val Green1 = Color(0xFF253627)
    val Green2 = Color(0xFF375239)
    val Green3 = Color(0xFF436946)
    val Green4 = Color(0xFF4E8052)
    val Green5 = Color(0xFF57965C)
    val Green6 = Color(0xFF5FAD65)
    val Green7 = Color(0xFF73BD79)
    val Green8 = Color(0xFF89CC8E)
    val Green9 = Color(0xFFA0DBA5)
    val Green10 = Color(0xFFB9EBBD)
    val Green11 = Color(0xFFD4FAD7)

    val Yellow1 = Color(0xFF3D3223)
    val Yellow2 = Color(0xFF5E4D33)
    val Yellow3 = Color(0xFF826A41)
    val Yellow4 = Color(0xFF9E814A)
    val Yellow5 = Color(0xFFBA9752)
    val Yellow6 = Color(0xFFD6AE58)
    val Yellow7 = Color(0xFFF2C55C)
    val Yellow8 = Color(0xFFF5D273)
    val Yellow9 = Color(0xFFF7DE8B)
    val Yellow10 = Color(0xFFFCEBA4)
    val Yellow11 = Color(0xFFFFF6BD)

    val Red1 = Color(0xFF402929)
    val Red2 = Color(0xFF5E3838)
    val Red3 = Color(0xFF7A4343)
    val Red4 = Color(0xFF9C4E4E)
    val Red5 = Color(0xFFBD5757)
    val Red6 = Color(0xFFDB5C5C)
    val Red7 = Color(0xFFE37774)
    val Red8 = Color(0xFFEB938D)
    val Red9 = Color(0xFFF2B1AA)
    val Red10 = Color(0xFFF7CCC6)
    val Red11 = Color(0xFFFAE3DE)

    val Orange1 = Color(0xFF45322B)
    val Orange2 = Color(0xFF614438)
    val Orange3 = Color(0xFF825845)
    val Orange4 = Color(0xFFA36B4E)
    val Orange5 = Color(0xFFC27A53)
    val Orange6 = Color(0xFFE08855)
    val Orange7 = Color(0xFFE5986C)
    val Orange8 = Color(0xFFF0AC81)
    val Orange9 = Color(0xFFF5BD98)
    val Orange10 = Color(0xFFFACEAF)
    val Orange11 = Color(0xFFFFDFC7)

    override val isDark: Boolean = true

    val NormalAreaColors = AreaColors(
        text = Grey12,
        foreground = Color.Unspecified,
        startBackground = Grey2,
        endBackground = Grey2,
        startBorderColor = Grey1,
        endBorderColor = Grey1,
        focusColor = Blue6,
    )

    val InactiveAreaColors = NormalAreaColors.copy(
        text = Grey6
    )

    val ErrorAreaColors = NormalAreaColors.copy(
        text = Red6
    )

    val ErrorInactiveAreaColors = ErrorAreaColors

    val DisabledAreaColors = NormalAreaColors.copy(
        text = Grey6,
        foreground = Grey6,
        startBackground = Grey2,
        endBackground = Grey2,
        startBorderColor = Grey4,
        endBorderColor = Grey4
    )

    val HoverAreaColors = NormalAreaColors.copy(
        startBackground = Grey3,
        endBackground = Grey3,
    )

    val PressedAreaColors = NormalAreaColors.copy(
        startBackground = Grey5,
        endBackground = Grey5,
    )

    val FocusAreaColors = NormalAreaColors.copy()

    val SelectionAreaColors = NormalAreaColors.copy(
        startBackground = Blue2,
        endBackground = Blue2,
    )

    val SelectionInactiveAreaColors = NormalAreaColors.copy(
        startBackground = Grey4,
        endBackground = Grey4,
    )

    val MainToolBarColors = MainToolBarColors(
        isDark = true,
        normalAreaColors = AreaColors(
            text = Grey13,
            foreground = Color.Unspecified,
            startBackground = Grey2,
            endBackground = Grey2,
            startBorderColor = Grey1,
            endBorderColor = Grey1,
            focusColor = Blue6,
        ),
        inactiveAreaColors = AreaColors(
            text = Grey13,
            foreground = Color.Unspecified,
            startBackground = Grey3,
            endBackground = Grey3,
            startBorderColor = Grey1,
            endBorderColor = Grey1,
            focusColor = Blue6,
        ),
        actionButtonColors = ActionButtonColors(
            normalAreaColors = AreaColors(
                text = Grey13,
                foreground = Color.Unspecified,
                startBackground = Color.Unspecified,
                endBackground = Color.Unspecified,
                startBorderColor = Color.Unspecified,
                endBorderColor = Color.Unspecified,
                focusColor = Blue6,
            ),
            hoverAreaColors = AreaColors(
                text = Grey13,
                foreground = Color.Unspecified,
                startBackground = Grey3,
                endBackground = Grey3,
                startBorderColor = Color.Unspecified,
                endBorderColor = Color.Unspecified,
                focusColor = Blue6,
            ),
            pressedAreaColors = AreaColors(
                text = Grey13,
                foreground = Color.Unspecified,
                startBackground = Grey3,
                endBackground = Grey3,
                startBorderColor = Color.Unspecified,
                endBorderColor = Color.Unspecified,
                focusColor = Blue6,
            ),
            disabledAreaColors = AreaColors(
                text = Grey13,
                foreground = Grey6,
                startBackground = Color.Unspecified,
                endBackground = Color.Unspecified,
                startBorderColor = Color.Unspecified,
                endBorderColor = Color.Unspecified,
                focusColor = Blue6,
            ),
        )
    )

    val WindowsCloseWindowButtonColors = ActionButtonColors(
        normalAreaColors = AreaColors(
            text = Grey13,
            foreground = Color.Unspecified,
            startBackground = Color.Unspecified,
            endBackground = Color.Unspecified,
            startBorderColor = Color.Unspecified,
            endBorderColor = Color.Unspecified,
            focusColor = Blue6,
        ),
        hoverAreaColors = AreaColors(
            text = Grey13,
            foreground = Color.Unspecified,
            startBackground = Red6,
            endBackground = Red6,
            startBorderColor = Color.Unspecified,
            endBorderColor = Color.Unspecified,
            focusColor = Red6,
        ),
        pressedAreaColors = AreaColors(
            text = Grey13,
            foreground = Color.Unspecified,
            startBackground = Red6,
            endBackground = Red6,
            startBorderColor = Color.Unspecified,
            endBorderColor = Color.Unspecified,
            focusColor = Red6,
        ),
        disabledAreaColors = AreaColors(
            text = Grey13,
            foreground = Grey6,
            startBackground = Color.Unspecified,
            endBackground = Color.Unspecified,
            startBorderColor = Color.Unspecified,
            endBorderColor = Color.Unspecified,
            focusColor = Blue6,
        ),
    )

    val ActionButtonColors = ActionButtonColors(
        normalAreaColors = NormalAreaColors.copy(
            startBackground = Color.Unspecified,
            endBackground = Color.Unspecified,
        ),
        hoverAreaColors = HoverAreaColors.copy(
            startBackground = Grey3,
            endBackground = Grey3,
        ),
        pressedAreaColors = PressedAreaColors.copy(
            startBackground = Grey5,
            endBackground = Grey5,
        ),
        disabledAreaColors = DisabledAreaColors.copy(
            startBackground = Color.Unspecified,
            endBackground = Color.Unspecified,
        )
    )

    val CheckBoxColors = CheckBoxColors(
        normalAreaColors = NormalAreaColors.copy(
            foreground = Grey14,
            startBackground = Grey2,
            endBackground = Grey2,
            startBorderColor = Grey5,
            endBorderColor = Grey5,
        ),
        selectionAreaColors = SelectionAreaColors.copy(
            foreground = Grey14,
            startBackground = Blue6,
            endBackground = Blue6,
            startBorderColor = Blue6,
            endBorderColor = Blue6,
        ),
        focusAreaColors = FocusAreaColors.copy(
            foreground = Grey14,
            startBackground = Blue6,
            endBackground = Blue6,
            startBorderColor = Color(0xFF101012),
            endBorderColor = Color(0xFF101012),
        ),
        disabledAreaColors = DisabledAreaColors.copy(
            foreground = Grey5,
            startBackground = Grey2,
            endBackground = Grey2,
            startBorderColor = Grey3,
            endBorderColor = Grey3,
        )
    )

    val RadioButtonColors = RadioButtonColors(
        normalAreaColors = NormalAreaColors.copy(
            foreground = Grey14,
            startBackground = Grey2,
            endBackground = Grey2,
            startBorderColor = Grey5,
            endBorderColor = Grey5,
        ),
        selectionAreaColors = SelectionAreaColors.copy(
            foreground = Grey14,
            startBackground = Blue6,
            endBackground = Blue6,
            startBorderColor = Blue6,
            endBorderColor = Blue6,
        ),
        focusAreaColors = FocusAreaColors.copy(
            foreground = Grey14,
            startBackground = Blue6,
            endBackground = Blue6,
            startBorderColor = Color(0xFF101012),
            endBorderColor = Color(0xFF101012),
        ),
        disabledAreaColors = DisabledAreaColors.copy(
            foreground = Grey5,
            startBackground = Grey2,
            endBackground = Grey2,
            startBorderColor = Grey3,
            endBorderColor = Grey3,
        )
    )

    val PrimaryButtonColors = ButtonColors(
        normalAreaColors = NormalAreaColors.copy(
            text = Grey14,
            startBackground = Blue6,
            endBackground = Blue6,
            startBorderColor = Blue6,
            endBorderColor = Blue6,
        ),
        focusAreaColors = FocusAreaColors.copy(
            text = Grey14,
            startBackground = Blue6,
            endBackground = Blue6,
            startBorderColor = Grey1,
            endBorderColor = Grey1,
        ),
        disabledAreaColors = DisabledAreaColors.copy(
            text = Grey5,
            startBackground = Grey2,
            endBackground = Grey2,
            startBorderColor = Grey3,
            endBorderColor = Grey3,
        )
    )

    val OutlineButtonColors = ButtonColors(
        normalAreaColors = NormalAreaColors.copy(
            text = Grey13,
            startBackground = Grey2,
            endBackground = Grey2,
            startBorderColor = Grey5,
            endBorderColor = Grey5,
        ),
        focusAreaColors = FocusAreaColors.copy(
            text = Grey13,
            startBackground = Grey2,
            endBackground = Grey2,
            startBorderColor = Grey5,
            endBorderColor = Grey5,
        ),
        disabledAreaColors = DisabledAreaColors.copy(
            text = Grey5,
            startBackground = Grey2,
            endBackground = Grey2,
            startBorderColor = Grey3,
            endBorderColor = Grey3,
        )
    )

    val LinkColors = LinkColors(
        normalAreaColors = NormalAreaColors.copy(
            text = Blue8,
        ),
        hoverAreaColors = HoverAreaColors.copy(
            text = Blue8,
        ),
        pressedAreaColors = PressedAreaColors.copy(
            text = Blue8,
        ),
        focusAreaColors = FocusAreaColors.copy(
            text = Blue8,
        ),
        disabledAreaColors = NormalAreaColors.copy(
            text = Grey5,
        ),
        visitedAreaColors = NormalAreaColors.copy(
            text = Blue4,
        )
    )

    val SegmentedButtonColors = SegmentedButtonColors(

        normalAreaColors = NormalAreaColors.copy(
            text = Grey13,
            startBackground = Grey2,
            endBackground = Grey2,
            startBorderColor = Grey5,
            endBorderColor = Grey5,
        ),
        focusAreaColors = FocusAreaColors.copy(
            text = Grey13,
            startBackground = Grey2,
            endBackground = Grey2,
            startBorderColor = Grey3,
            endBorderColor = Grey3,
        ),
        itemNormalAreaColors = NormalAreaColors.copy(
            text = Grey13,
            startBackground = Color.Unspecified,
            endBackground = Color.Unspecified,
            startBorderColor = Color.Unspecified,
            endBorderColor = Color.Unspecified,
        ),
        itemHoverAreaColors = HoverAreaColors.copy(
            text = Grey13,
            startBackground = Grey3,
            endBackground = Grey3,
            startBorderColor = Color.Unspecified,
            endBorderColor = Color.Unspecified,
        ),
        itemPressedAreaColors = PressedAreaColors.copy(
            text = Grey13,
            startBackground = Grey3,
            endBackground = Grey3,
            startBorderColor = Color.Unspecified,
            endBorderColor = Color.Unspecified,
        ),
        itemSelectionAreaColors = SelectionAreaColors.copy(
            text = Grey13,
            startBackground = Grey3,
            endBackground = Grey3,
            startBorderColor = Grey5,
            endBorderColor = Grey5,
        ),
        itemSelectedFocusAreaColors = SelectionAreaColors.copy(
            text = Grey13,
            startBackground = Blue3,
            endBackground = Blue3,
            startBorderColor = Grey3,
            endBorderColor = Grey3,
        )
    )

    val ToolTipColors = ToolTipColors(
        isDark = true,
        normalAreaColors = NormalAreaColors.copy(
            text = Grey13,
            startBackground = Grey3,
            endBackground = Grey3,
            startBorderColor = Grey4,
            endBorderColor = Grey4,
        )
    )

    val TextFieldColors = TextFieldColors(
        normalAreaColors = NormalAreaColors.copy(
            text = Grey13,
            startBackground = Grey2,
            endBackground = Grey2,
            startBorderColor = Grey5,
            endBorderColor = Grey5,
        ),
        focusAreaColors = FocusAreaColors.copy(
            text = Grey13,
            startBackground = Grey2,
            endBackground = Grey2,
            startBorderColor = Grey2,
            endBorderColor = Grey2,
        ),
        disabledAreaColors = DisabledAreaColors.copy(
            text = Grey5,
            startBackground = Grey2,
            endBackground = Grey2,
            startBorderColor = Grey3,
            endBorderColor = Grey3,
        ),
        errorAreaColors = ErrorAreaColors.copy(
            text = Red6,
            startBackground = Grey2,
            endBackground = Grey2,
            startBorderColor = Red6,
            endBorderColor = Red6,
            focusColor = Red6,
        ),
        errorFocusAreaColors = ErrorAreaColors.copy(
            text = Red6,
            startBackground = Grey2,
            endBackground = Grey2,
            startBorderColor = Grey2,
            endBorderColor = Grey2,
            focusColor = Red6,
        )
    )

    val DropdownMenuColors = DropdownMenuColors(
        normalAreaColors = NormalAreaColors.copy(
            text = Grey13,
            startBackground = Grey2,
            endBackground = Grey2,
            startBorderColor = Grey4,
            endBorderColor = Grey4,
        ),
        hoverAreaColors = HoverAreaColors.copy(
            text = Grey13,
            startBackground = Blue2,
            endBackground = Blue2,
            startBorderColor = Blue2,
            endBorderColor = Blue2,
        ),
        pressedAreaColors = PressedAreaColors.copy(
            text = Grey13,
            startBackground = Blue2,
            endBackground = Blue2,
            startBorderColor = Blue2,
            endBorderColor = Blue2,
        ),
        focusAreaColors = FocusAreaColors.copy(
            text = Grey13,
            startBackground = Blue2,
            endBackground = Blue2,
            startBorderColor = Blue2,
            endBorderColor = Blue2,
        ),
    )

    val ComboBoxColors = ComboBoxColors(
        normalAreaColors = NormalAreaColors.copy(
            text = Grey13,
            startBackground = Grey2,
            endBackground = Grey2,
            startBorderColor = Grey5,
            endBorderColor = Grey5,
        ),
        focusAreaColors = FocusAreaColors.copy(
            text = Grey13,
            startBackground = Grey2,
            endBackground = Grey2,
            startBorderColor = Grey5,
            endBorderColor = Grey5,
        ),
        disabledAreaColors = DisabledAreaColors.copy(
            text = Grey5,
            startBackground = Grey2,
            endBackground = Grey2,
            startBorderColor = Grey3,
            endBorderColor = Grey3,
        ),
        dropdownMenuColors = DropdownMenuColors
    )

    val ContextMenuColors = ContextMenuColors(
        normalAreaColors = NormalAreaColors.copy(
            text = Grey13,
            startBackground = Grey2,
            endBackground = Grey2,
            startBorderColor = Grey4,
            endBorderColor = Grey4,
        ),
        hoverAreaColors = HoverAreaColors.copy(
            text = Grey13,
            startBackground = Blue2,
            endBackground = Blue2,
            startBorderColor = Blue2,
            endBorderColor = Blue2,
        ),
        pressedAreaColors = PressedAreaColors.copy(
            text = Grey13,
            startBackground = Blue2,
            endBackground = Blue2,
            startBorderColor = Blue2,
            endBorderColor = Blue2,
        ),
        focusAreaColors = FocusAreaColors.copy(
            text = Grey13,
            startBackground = Blue2,
            endBackground = Blue2,
            startBorderColor = Blue2,
            endBorderColor = Blue2,
        ),
    )

    val ToolBarActionButtonColors = ToolBarActionButtonColors(
        normalAreaColors = NormalAreaColors.copy(
            startBackground = Color.Unspecified,
            endBackground = Color.Unspecified,
        ),
        hoverAreaColors = HoverAreaColors.copy(
            startBackground = Grey4,
            endBackground = Grey4,
        ),
        pressedAreaColors = PressedAreaColors.copy(
            startBackground = Grey4,
            endBackground = Grey4,
        ),
        disabledAreaColors = DisabledAreaColors.copy(
            startBackground = Color.Unspecified,
            endBackground = Color.Unspecified,
        ),
        inactiveAreaColors = NormalAreaColors.copy(
            startBackground = Color.Unspecified,
            endBackground = Color.Unspecified,
        ),
        selectionAreaColors = SelectionAreaColors.copy(
            foreground = Grey13,
            startBackground = Blue6,
            endBackground = Blue6,
        ),
        inactiveSelectionAreaColors = SelectionInactiveAreaColors.copy(
            startBackground = Grey4,
            endBackground = Grey4,
        )
    )
    val ProgressBarColors = ProgressBarColors(
        normalAreaColors = NormalAreaColors.copy(
            foreground = Blue7,
            startBackground = Grey4,
            endBackground = Grey4,
        ),
        indeterminateAreaColors = NormalAreaColors.copy(
            foreground = Blue9,
            startBackground = Blue9,
            endBackground = Blue5,
        ),
    )

    val TabColors = TabColors(
        normalAreaColors = NormalAreaColors.copy(
            startBackground = Color.Unspecified,
            endBackground = Color.Unspecified,
        ),
        hoverAreaColors = HoverAreaColors.copy(
            startBackground = Grey4,
            endBackground = Grey4,
        ),
        pressedAreaColors = PressedAreaColors.copy(
            startBackground = Grey4,
            endBackground = Grey4,
        ),
        inactiveAreaColors = NormalAreaColors.copy(
            startBackground = Color.Unspecified,
            endBackground = Color.Unspecified,
        ),
        selectionAreaColors = SelectionAreaColors.copy(
            focusColor = Blue7,
            startBackground = Color.Unspecified,
            endBackground = Color.Unspecified,
        ),
        inactiveSelectionAreaColors = SelectionInactiveAreaColors.copy(
            focusColor = Grey6,
            startBackground = Color.Unspecified,
            endBackground = Color.Unspecified,
        )
    )

    val CloseableTabColors = TabColors(
        normalAreaColors = NormalAreaColors.copy(
            startBackground = Color.Unspecified,
            endBackground = Color.Unspecified,
        ),
        hoverAreaColors = HoverAreaColors.copy(
            startBackground = Color.Unspecified,
            endBackground = Color.Unspecified,
        ),
        pressedAreaColors = PressedAreaColors.copy(
            startBackground = Color.Unspecified,
            endBackground = Color.Unspecified,
        ),
        inactiveAreaColors = NormalAreaColors.copy(
            startBackground = Color.Unspecified,
            endBackground = Color.Unspecified,
        ),
        selectionAreaColors = SelectionAreaColors.copy(
            focusColor = Blue7,
            startBackground = Color.Unspecified,
            endBackground = Color.Unspecified,
        ),
        inactiveSelectionAreaColors = SelectionInactiveAreaColors.copy(
            focusColor = Grey6,
            startBackground = Color.Unspecified,
            endBackground = Color.Unspecified,
        )
    )

    val DefaultTextStyle = TextStyle(
        fontFamily = Fonts.Inter, fontSize = 13.sp, fontWeight = FontWeight.Normal, color = Color.Unspecified
    )

    val DefaultBoldTextStyle = DefaultTextStyle.copy(fontWeight = FontWeight.Bold)

    val ParagraphTextStyle = DefaultTextStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 19.sp)

    val MediumTextStyle = DefaultTextStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 15.sp)

    val MediumBoldTextStyle = MediumTextStyle.copy(fontWeight = FontWeight.Bold)

    val SmallTextStyle = DefaultTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Normal, lineHeight = 14.sp)

    val H0TextStyle = DefaultTextStyle.copy(fontSize = 25.sp, fontWeight = FontWeight.Medium)

    val H1TextStyle = DefaultTextStyle.copy(fontSize = 22.sp, fontWeight = FontWeight.Medium)

    val H2TextStyle = DefaultTextStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Normal)

    val H2BoldTextStyle = H2TextStyle.copy(fontWeight = FontWeight.Bold)

    val H3TextStyle = DefaultTextStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp)

    val H3BoldTextStyle = H3TextStyle.copy(fontWeight = FontWeight.Bold)

    override fun provideValues(): Array<ProvidedValue<*>> {
        return arrayOf(
            LocalIsDarkTheme provides isDark,
            LocalAreaColors provides NormalAreaColors,
            LocalInactiveAreaColors provides InactiveAreaColors,
            LocalErrorAreaColors provides ErrorAreaColors,
            LocalErrorInactiveAreaColors provides ErrorInactiveAreaColors,
            LocalDisabledAreaColors provides DisabledAreaColors,
            LocalHoverAreaColors provides HoverAreaColors,
            LocalPressedAreaColors provides PressedAreaColors,
            LocalFocusAreaColors provides FocusAreaColors,
            LocalSelectionAreaColors provides SelectionAreaColors,
            LocalSelectionInactiveAreaColors provides SelectionInactiveAreaColors,
            LocalMainToolBarColors provides MainToolBarColors,
            LocalActionButtonColors provides ActionButtonColors,
            LocalCheckBoxColors provides CheckBoxColors,
            LocalRadioButtonColors provides RadioButtonColors,
            LocalPrimaryButtonColors provides PrimaryButtonColors,
            LocalOutlineButtonColors provides OutlineButtonColors,
            LocalLinkColors provides LinkColors,
            LocalSegmentedButtonColors provides SegmentedButtonColors,
            LocalToolTipColors provides ToolTipColors,
            LocalTextFieldColors provides TextFieldColors,
            LocalDropdownMenuColors provides DropdownMenuColors,
            LocalComboBoxColors provides ComboBoxColors,
            LocalContextMenuColors provides ContextMenuColors,
            LocalToolBarActionButtonColors provides ToolBarActionButtonColors,
            LocalProgressBarColors provides ProgressBarColors,
            LocalTabColors provides TabColors,
            LocalCloseableTabColors provides CloseableTabColors,
            LocalWindowsCloseWindowButtonColors provides WindowsCloseWindowButtonColors,
            LocalContextMenuRepresentation provides JbContextMenuRepresentation(ContextMenuColors),
            LocalDefaultTextStyle provides DefaultTextStyle,
            LocalDefaultBoldTextStyle provides DefaultBoldTextStyle,
            LocalParagraphTextStyle provides ParagraphTextStyle,
            LocalMediumTextStyle provides MediumTextStyle,
            LocalMediumBoldTextStyle provides MediumBoldTextStyle,
            LocalSmallTextStyle provides SmallTextStyle,
            LocalH0TextStyle provides H0TextStyle,
            LocalH1TextStyle provides H1TextStyle,
            LocalH2TextStyle provides H2TextStyle,
            LocalH2BoldTextStyle provides H2BoldTextStyle,
            LocalH3TextStyle provides H3TextStyle,
            LocalH3BoldTextStyle provides H3BoldTextStyle,
        )
    }
}

@Composable
fun DarkTheme(content: @Composable () -> Unit) {
    DarkTheme.provide(content)
}
