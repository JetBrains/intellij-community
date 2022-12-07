package org.jetbrains.jewel.themes.expui.standalone.style

interface AreaProvider {

    val normalAreaColors: AreaColors
}

interface ErrorAreProvider : AreaProvider {

    val errorAreaColors: AreaColors
}

interface ErrorFocusAreaProvider : ErrorAreProvider, FocusAreaProvider {

    val errorFocusAreaColors: AreaColors
}

interface SelectionAreaProvider : AreaProvider {

    val selectionAreaColors: AreaColors
}

interface FocusAreaProvider : AreaProvider {

    val focusAreaColors: AreaColors
}

interface DisabledAreaProvider : AreaProvider {

    val disabledAreaColors: AreaColors
}

interface HoverAreaProvider : AreaProvider {

    val hoverAreaColors: AreaColors
}

interface PressedAreaProvider : AreaProvider {

    val pressedAreaColors: AreaColors
}

interface InactiveAreaProvider : AreaProvider {

    val inactiveAreaColors: AreaColors
}

interface InactiveErrorAreaProvider : ErrorAreProvider, InactiveAreaProvider {

    val inactiveErrorAreaColors: AreaColors
}

interface InactiveSelectionAreaProvider : SelectionAreaProvider, InactiveAreaProvider {

    val inactiveSelectionAreaColors: AreaColors
}

interface InactiveFocusAreaProvider : FocusAreaProvider, InactiveAreaProvider {

    val inactiveFocusAreaColors: AreaColors
}
