// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab

import com.intellij.platform.ide.nonModalWelcomeScreen.NonModalWelcomeScreenBundle
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.IntelliJSpacingConfiguration
import com.intellij.ui.dsl.builder.SegmentedButton
import com.intellij.ui.dsl.builder.components.SegmentedButtonComponent
import com.intellij.ui.dsl.builder.components.SegmentedButtonComponent.Companion.whenItemSelected
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * The unscaled gap above and below the text of a segment. The height of a segment is the larger of the text with
 * these gaps and `Button.minimumSize` less 2. The minimum depends on the theme, and this gap is big enough to win.
 */
private const val SEGMENT_VERTICAL_GAP = 5

/** The unscaled distance from the left edge of the sample editor to its text. */
private const val SAMPLE_TEXT_INDENT = 10

/**
 * Creates the header of the theme chooser: the Light/Dark switch on the left and, when [hasSample] is `true`,
 * the title over the code sample on the right. [onFilterSelected] gets the brightness that the user picks.
 */
internal fun createThemeChooserHeader(
  initialFilter: ThemeBrightnessFilter,
  hasSample: Boolean,
  onFilterSelected: (ThemeBrightnessFilter) -> Unit,
): JComponent {
  val header = JPanel(BorderLayout())
  header.isOpaque = false
  header.border = JBUI.Borders.empty(8, 8, 4, 8)
  header.add(createBrightnessSwitch(initialFilter, onFilterSelected), BorderLayout.WEST)
  if (hasSample) {
    header.add(createSampleTitle(), BorderLayout.EAST)
  }
  return header
}

private fun createBrightnessSwitch(initialFilter: ThemeBrightnessFilter, onFilterSelected: (ThemeBrightnessFilter) -> Unit): JComponent {
  val switch = SegmentedButtonComponent<ThemeBrightnessFilter> { filter ->
    val textKey = when (filter) {
      ThemeBrightnessFilter.LIGHT -> "welcome.screen.right.tab.theme.brightness.light"
      ThemeBrightnessFilter.DARK -> "welcome.screen.right.tab.theme.brightness.dark"
    }
    SegmentedButton.createPresentation(text = NonModalWelcomeScreenBundle.message(textKey))
  }
  switch.spacing = object : IntelliJSpacingConfiguration() {
    override val segmentedButtonVerticalGap: Int = SEGMENT_VERTICAL_GAP
  }
  switch.items = listOf(ThemeBrightnessFilter.DARK, ThemeBrightnessFilter.LIGHT)
  switch.accessibleContext.accessibleName = NonModalWelcomeScreenBundle.message("welcome.screen.right.tab.theme.brightness.accessible.name")
  // Select before the listener is added, so that the initial state does not count as a choice.
  switch.selectedItem = initialFilter
  switch.whenItemSelected(listener = onFilterSelected)
  return switch
}

/** The title is as wide as the sample, so that it starts over the text of the sample. */
private fun createSampleTitle(): JComponent {
  val title = JBLabel(NonModalWelcomeScreenBundle.message("welcome.screen.right.tab.theme.preview.title"))
  title.foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
  title.border = JBUI.Borders.emptyLeft(SAMPLE_TEXT_INDENT)

  val box = object : JPanel(BorderLayout()) {
    override fun getPreferredSize(): Dimension = Dimension(JBUI.scale(SAMPLE_WIDTH), super.getPreferredSize().height)
  }
  box.isOpaque = false
  box.add(title, BorderLayout.WEST)
  return box
}
