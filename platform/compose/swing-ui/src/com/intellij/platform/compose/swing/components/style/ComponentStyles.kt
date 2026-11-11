// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing.components.style

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.compose.swing.components.style.Buttons
import org.jetbrains.compose.swing.components.style.ButtonStyleScope
import org.jetbrains.compose.swing.components.style.ComboBoxStyleScope
import org.jetbrains.compose.swing.components.style.ComboBoxes
import org.jetbrains.compose.swing.components.style.LabelStyleScope
import org.jetbrains.compose.swing.components.style.Labels
import org.jetbrains.compose.swing.components.style.Panels
import org.jetbrains.compose.swing.components.style.Separators
import org.jetbrains.compose.swing.components.style.TextStyleScope
import org.jetbrains.compose.swing.style.ComponentStyleScope
import org.jetbrains.compose.swing.style.ContainerStyleScope
import org.jetbrains.compose.swing.style.NestedStyleKey
import org.jetbrains.compose.swing.style.NestedStyleProviderScope
import org.jetbrains.compose.swing.style.SwingComponentStyleScope
import org.jetbrains.compose.swing.style.SwingStyleKind
import org.jetbrains.compose.swing.style.nestingStyleKind

/** Styles for every `OptionButton` below the provider. An option button also applies `Buttons`, before this key. */
@ApiStatus.Experimental
public val OptionButtons: NestedStyleKey<ButtonStyleScope> = NestedStyleKey("OptionButtons")

/** Styles for every `ThreeStateCheckBox` below the provider. */
@ApiStatus.Experimental
public val ThreeStateCheckBoxes: NestedStyleKey<ButtonStyleScope> = NestedStyleKey("ThreeStateCheckBoxes")

/** Styles for every `Comment` below the provider, `FormComment` and the comments of a `FormRow` included. */
@ApiStatus.Experimental
public val Comments: NestedStyleKey<TextStyleScope> = NestedStyleKey("Comments")

/** Styles for every `TitledSeparator` below the provider. A titled separator also applies `Separators`, before this key. */
@ApiStatus.Experimental
public val TitledSeparators: NestedStyleKey<SwingComponentStyleScope> = NestedStyleKey("TitledSeparators")

/** Styles for every `SearchTextField` below the provider. */
@ApiStatus.Experimental
public val SearchTextFields: NestedStyleKey<SwingComponentStyleScope> = NestedStyleKey("SearchTextFields")

/** Styles for every `SegmentedButton` below the provider, as a row of buttons or as the combo box it falls back to. */
@ApiStatus.Experimental
public val SegmentedButtons: NestedStyleKey<SwingComponentStyleScope> = NestedStyleKey("SegmentedButtons")

/** Styles for every `FormPanel` below the provider. A form panel also applies `Panels`, before this key. */
@ApiStatus.Experimental
public val FormPanels: NestedStyleKey<ContainerStyleScope> = NestedStyleKey("FormPanels")

internal val OptionButtonKind: SwingStyleKind<ButtonStyleScope, Unit> =
  nestingStyleKind({ base, _ -> object : ButtonStyleScope, ComponentStyleScope by base {} }) {
    applyNestedStyle(Buttons)
    applyNestedStyle(OptionButtons)
  }

internal val ThreeStateCheckBoxKind: SwingStyleKind<ButtonStyleScope, Unit> =
  nestingStyleKind({ base, _ -> object : ButtonStyleScope, ComponentStyleScope by base {} }) {
    applyNestedStyle(ThreeStateCheckBoxes)
  }

internal val ComboBoxKind: SwingStyleKind<ComboBoxStyleScope, Unit> =
  nestingStyleKind({ base, _ -> object : ComboBoxStyleScope, ComponentStyleScope by base {} }) {
    applyNestedStyle(ComboBoxes)
  }

internal val CommentKind: SwingStyleKind<TextStyleScope, Unit> =
  nestingStyleKind({ base, _ -> object : TextStyleScope, ComponentStyleScope by base {} }) {
    applyNestedStyle(Comments)
  }

internal val TitledSeparatorKind: SwingStyleKind<SwingComponentStyleScope, Unit> =
  nestingStyleKind({ base, _ -> object : SwingComponentStyleScope, ComponentStyleScope by base {} }) {
    applyNestedStyle(Separators)
    applyNestedStyle(TitledSeparators)
  }

internal val SearchTextFieldKind: SwingStyleKind<SwingComponentStyleScope, Unit> =
  nestingStyleKind({ base, _ -> object : SwingComponentStyleScope, ComponentStyleScope by base {} }) {
    applyNestedStyle(SearchTextFields)
  }

internal val SegmentedButtonKind: SwingStyleKind<SwingComponentStyleScope, Unit> =
  nestingStyleKind({ base, _ -> object : SwingComponentStyleScope, ComponentStyleScope by base {} }) {
    applyNestedStyle(SegmentedButtons)
  }

/** The label that opens a form row. */
internal val FormLabelKind: SwingStyleKind<LabelStyleScope, Unit> =
  nestingStyleKind({ base, _ -> object : LabelStyleScope, ComponentStyleScope by base {} }) {
    applyNestedStyle(Labels)
  }

/** The separator of `FormSeparator`. */
internal val SeparatorKind: SwingStyleKind<SwingComponentStyleScope, Unit> =
  nestingStyleKind({ base, _ -> object : SwingComponentStyleScope, ComponentStyleScope by base {} }) {
    applyNestedStyle(Separators)
  }

internal val FormPanelKind: SwingStyleKind<ContainerStyleScope, Unit> =
  nestingStyleKind({ base, _ -> object : ContainerStyleScope, NestedStyleProviderScope by base {} }) {
    applyNestedStyle(Panels)
    applyNestedStyle(FormPanels)
  }
