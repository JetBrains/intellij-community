// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.colors

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.EditorSchemeAttributeDescriptor
import com.intellij.openapi.editor.colors.EditorSchemeAttributeDescriptorWithPath
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.ColorPanel
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.DslComponentProperty
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.VerticalComponentGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.util.BitUtil
import com.intellij.util.EventDispatcher
import com.intellij.util.FontUtil
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.util.Locale
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextPane

class ColorAndFontDescriptionPanel : JPanel(BorderLayout()), OptionsPanelImpl.ColorDescriptionPanel {
  private val myDispatcher = EventDispatcher.create(OptionsPanelImpl.ColorDescriptionPanel.Listener::class.java)

  private val myEffectsMap: Map<String, EffectType> = linkedMapOf(
    ApplicationBundle.message("combobox.effect.underscored") to EffectType.LINE_UNDERSCORE,
    ApplicationBundle.message("combobox.effect.boldunderscored") to EffectType.BOLD_LINE_UNDERSCORE,
    ApplicationBundle.message("combobox.effect.underwaved") to EffectType.WAVE_UNDERSCORE,
    ApplicationBundle.message("combobox.effect.bordered") to EffectType.BOXED,
    ApplicationBundle.message("combobox.effect.strikeout") to EffectType.STRIKEOUT,
    ApplicationBundle.message("combobox.effect.bold.dottedline") to EffectType.BOLD_DOTTED_LINE,
    ApplicationBundle.message("combobox.effect.faded") to EffectType.FADED)

  private val myBackgroundChooser = ColorPanel()
  private val myForegroundChooser = ColorPanel()
  private val myEffectsColorChooser = ColorPanel()
  private val myErrorStripeColorChooser = ColorPanel()
  private val myInheritanceLabel = JTextPane()

  private var myCurrentDescription: ColorAndFontDescription? = null

  private val myFontTypeRevert = RevertButton(ApplicationBundle.message("color.option.revert.font.style")) {
    revertChanges {
      it.revertChannel(ColorAndFontDescription.Channel.BOLD)
      it.revertChannel(ColorAndFontDescription.Channel.ITALIC)
    }
  }
  private val myForegroundRevert = RevertButton(ApplicationBundle.message("color.option.revert.foreground")) {
    revertChanges { it.revertChannel(ColorAndFontDescription.Channel.FOREGROUND) }
  }
  private val myBackgroundRevert = RevertButton(ApplicationBundle.message("color.option.revert.background")) {
    revertChanges { it.revertChannel(ColorAndFontDescription.Channel.BACKGROUND) }
  }
  private val myErrorStripeRevert = RevertButton(ApplicationBundle.message("color.option.revert.error.stripe")) {
    revertChanges { it.revertChannel(ColorAndFontDescription.Channel.ERROR_STRIPE) }
  }
  private val myEffectsRevert = RevertButton(ApplicationBundle.message("color.option.revert.effects")) {
    revertChanges { it.revertChannel(ColorAndFontDescription.Channel.EFFECTS) }
  }
  private val myInheritanceRevert = RevertButton(ApplicationBundle.message("color.option.revert.inheritance")) {
    revertChanges { it.revertInheritance() }
  }

  private lateinit var myContrastWarning: JEditorPane
  private lateinit var myContrastWarningRow: Row

  private lateinit var myCbBackground: JBCheckBox
  private lateinit var myCbForeground: JBCheckBox
  private lateinit var myCbEffects: JBCheckBox
  private lateinit var myCbErrorStripe: JBCheckBox
  private lateinit var myEffectsCombo: JComboBox<String>
  private lateinit var myCbBold: JBCheckBox
  private lateinit var myCbItalic: JBCheckBox
  private lateinit var myLabelFont: JLabel
  private lateinit var myInheritAttributesBox: JBCheckBox

  private var myUiEventsEnabled = true

  private val myPanel: DialogPanel = panel {
    row {
      myLabelFont = label(ApplicationBundle.message("label.font.type"))
        .resizableColumn()
        .component
      cell(myFontTypeRevert)
      myCbBold = checkBox(ApplicationBundle.message("checkbox.font.bold")).component
      myCbItalic = checkBox(ApplicationBundle.message("checkbox.font.italic")).component
    }
    row {
      myCbForeground = checkBox(ApplicationBundle.message("checkbox.color.foreground"))
        .resizableColumn()
        .component
      cell(myForegroundRevert)
      cell(myForegroundChooser)
    }
    row {
      myCbBackground = checkBox(ApplicationBundle.message("checkbox.color.background"))
        .resizableColumn()
        .component
      cell(myBackgroundRevert)
      cell(myBackgroundChooser)
    }
    row {
      myCbErrorStripe = checkBox(ApplicationBundle.message("checkbox.color.error.stripe.mark"))
        .resizableColumn()
        .component
      cell(myErrorStripeRevert)
      cell(myErrorStripeColorChooser)
    }
    row {
      myCbEffects = checkBox(ApplicationBundle.message("checkbox.color.effects"))
        .resizableColumn()
        .component
      cell(myEffectsRevert)
      cell(myEffectsColorChooser)
    }
    row {
      myEffectsCombo = comboBox(CollectionComboBoxModel(myEffectsMap.keys.toList()),
                                textListCellRenderer<String>(IdeBundle.message("label.invalid.color")) { it })
        .align(AlignX.RIGHT)
        .component
    }
    row {
      myInheritAttributesBox = checkBox(ApplicationBundle.message("label.inherit.attributes")).component
      cell(myInheritanceRevert)
    }.topGap(TopGap.MEDIUM)
    row {
      cell(JBScrollPane(myInheritanceLabel).apply { border = JBUI.Borders.empty() })
        .align(AlignX.FILL)
    }
    myContrastWarningRow = row {
      icon(AllIcons.General.Warning)
        .align(AlignY.TOP)
        .gap(RightGap.SMALL)
      // A fixed wrap width (in characters) keeps the label's preferred width below the panel's natural width,
      // so showing the warning never widens the right panel (its width is driven by preferred sizes).
      myContrastWarning = text("", maxLineLength = CONTRAST_WARNING_WRAP_LENGTH)
        .component
    }.layout(RowLayout.INDEPENDENT).visible(false)
  }

  init {
    add(myPanel, BorderLayout.CENTER)

    border = JBUI.Borders.empty(4, 0, 4, 4)
    myPanel.border = JBUI.Borders.empty(0, 10, 10, 10)

    val actionListener = ActionListener { e ->
      if (myUiEventsEnabled) {
        myErrorStripeColorChooser.isEnabled = myCbErrorStripe.isSelected
        myForegroundChooser.isEnabled = myCbForeground.isSelected
        myBackgroundChooser.isEnabled = myCbBackground.isSelected
        myEffectsColorChooser.isEnabled = myCbEffects.isSelected
        myEffectsCombo.isEnabled = myCbEffects.isSelected

        myDispatcher.multicaster.onSettingsChanged(e)
      }
    }

    for (c in arrayOf(myCbBackground, myCbForeground, myCbEffects, myCbErrorStripe, myCbItalic, myCbBold, myInheritAttributesBox)) {
      c.addActionListener(actionListener)
    }
    for (c in arrayOf(myBackgroundChooser, myForegroundChooser, myEffectsColorChooser, myErrorStripeColorChooser)) {
      c.addActionListener(actionListener)
    }
    myEffectsCombo.addActionListener(actionListener)

    @Suppress("HardCodedStringLiteral")
    Messages.configureMessagePaneUi(myInheritanceLabel, "<html>", null)
    myInheritanceLabel.addHyperlinkListener { e -> myDispatcher.multicaster.onHyperLinkClicked(e) }
    myInheritanceLabel.border = JBUI.Borders.empty(4, 0, 4, 4)
    myLabelFont.isVisible = false // hide for now as it doesn't look that good
  }

  override fun getPanel(): JComponent = this

  override fun resetDefault() {
    try {
      myUiEventsEnabled = false
      myCurrentDescription = null
      myLabelFont.isEnabled = false
      myCbBold.isSelected = false
      myCbBold.isEnabled = false
      myCbItalic.isSelected = false
      myCbItalic.isEnabled = false
      updateColorChooser(myCbForeground, myForegroundChooser, false, false, null)
      updateColorChooser(myCbBackground, myBackgroundChooser, false, false, null)
      updateColorChooser(myCbErrorStripe, myErrorStripeColorChooser, false, false, null)
      updateColorChooser(myCbEffects, myEffectsColorChooser, false, false, null)
      myEffectsCombo.isEnabled = false
      myInheritanceLabel.isVisible = false
      myInheritAttributesBox.isVisible = false
      updateModificationIndicators()
      updateContrastWarning()
    }
    finally {
      myUiEventsEnabled = true
    }
  }

  override fun reset(attrDescription: EditorSchemeAttributeDescriptor) {
    try {
      myUiEventsEnabled = false
      val description = attrDescription as? ColorAndFontDescription
      myCurrentDescription = description
      if (description == null) {
        updateModificationIndicators()
        updateContrastWarning()
        return
      }

      if (description.isFontEnabled) {
        myLabelFont.isEnabled = description.isEditable
        myCbBold.isEnabled = description.isEditable
        myCbItalic.isEnabled = description.isEditable
        val fontType = description.fontType
        myCbBold.isSelected = BitUtil.isSet(fontType, Font.BOLD)
        myCbItalic.isSelected = BitUtil.isSet(fontType, Font.ITALIC)
      }
      else {
        myLabelFont.isEnabled = false
        myCbBold.isSelected = false
        myCbBold.isEnabled = false
        myCbItalic.isSelected = false
        myCbItalic.isEnabled = false
      }

      updateColorChooser(myCbForeground, myForegroundChooser, description.isForegroundEnabled,
                         description.isForegroundChecked, description.foregroundColor, description.isTransparencyEnabled)

      updateColorChooser(myCbBackground, myBackgroundChooser, description.isBackgroundEnabled,
                         description.isBackgroundChecked, description.backgroundColor, description.isTransparencyEnabled)

      updateColorChooser(myCbErrorStripe, myErrorStripeColorChooser, description.isErrorStripeEnabled,
                         description.isErrorStripeChecked, description.errorStripeColor, description.isTransparencyEnabled)

      val effectType = description.effectType
      updateColorChooser(myCbEffects, myEffectsColorChooser, description.isEffectsColorEnabled,
                         description.isEffectsColorChecked, description.effectColor, description.isTransparencyEnabled)

      val name = ContainerUtil.reverseMap(myEffectsMap)[effectType]
      myEffectsCombo.model.selectedItem = name
      myEffectsCombo.isEnabled = (description.isEffectsColorEnabled && description.isEffectsColorChecked) && description.isEditable
      setInheritanceInfo(description)
      myLabelFont.isEnabled = myCbBold.isEnabled || myCbItalic.isEnabled
      updateModificationIndicators()
      updateContrastWarning()
    }
    finally {
      myUiEventsEnabled = true
    }
  }

  private fun updateContrastWarning() {
    val warningText = getContrastWarningText(myCurrentDescription)
    if (warningText != null) {
      myContrastWarning.text = warningText
    }
    myContrastWarningRow.visible(warningText != null)
  }

  private fun updateModificationIndicators() {
    val description = myCurrentDescription

    // Bold and Italic have their own indicators but share the row and its revert button
    val boldModified = description != null && description.isChannelModified(ColorAndFontDescription.Channel.BOLD)
    val italicModified = description != null && description.isChannelModified(ColorAndFontDescription.Channel.ITALIC)
    myFontTypeRevert.setRevertAvailable(boldModified || italicModified)
    myCbBold.foreground = if (boldModified) MODIFIED_ITEM_FOREGROUND else null
    myCbItalic.foreground = if (italicModified) MODIFIED_ITEM_FOREGROUND else null

    updateChannelIndicator(description, ColorAndFontDescription.Channel.FOREGROUND, myForegroundRevert, myCbForeground)
    updateChannelIndicator(description, ColorAndFontDescription.Channel.BACKGROUND, myBackgroundRevert, myCbBackground)
    updateChannelIndicator(description, ColorAndFontDescription.Channel.ERROR_STRIPE, myErrorStripeRevert, myCbErrorStripe)
    updateChannelIndicator(description, ColorAndFontDescription.Channel.EFFECTS, myEffectsRevert, myCbEffects)

    val inheritanceModified = description != null && description.isInheritanceModified
    myInheritanceRevert.setRevertAvailable(inheritanceModified)
    myInheritAttributesBox.foreground = if (inheritanceModified) MODIFIED_ITEM_FOREGROUND else null
  }

  private fun updateChannelIndicator(description: ColorAndFontDescription?,
                                     channel: ColorAndFontDescription.Channel,
                                     revertButton: RevertButton,
                                     checkBox: JCheckBox) {
    val modified = description != null && description.isChannelModified(channel)
    revertButton.setRevertAvailable(modified)
    checkBox.foreground = if (modified) MODIFIED_ITEM_FOREGROUND else null
  }

  private fun revertChanges(revertAction: (ColorAndFontDescription) -> Unit) {
    val description = myCurrentDescription ?: return
    revertAction(description)
    reset(description)
    myDispatcher.multicaster.onSettingsChanged(ActionEvent(this, ActionEvent.ACTION_PERFORMED, "revert"))
  }

  private fun setInheritanceInfo(description: ColorAndFontDescription) {
    val baseDescriptor = description.fallbackKeyDescriptor
    if (baseDescriptor != null) {
      val attrName = baseDescriptor.second.displayName
      val attrLabel = attrName.replace(EditorSchemeAttributeDescriptorWithPath.getNameSeparator().toRegex(),
                                       FontUtil.rightArrow(StartupUiUtil.labelFont))
      val settingsPage = baseDescriptor.first
      val tooltipText: String
      val labelText: String
      val div = HtmlChunk.div("text-align:right").attr("vertical-align", "top")
      if (settingsPage != null) {
        val pageName = settingsPage.displayName
        tooltipText = IdeBundle.message("tooltip.inherited.editor.color.scheme", pageName, attrLabel)
        labelText = div.children(HtmlChunk.link(pageName, attrLabel), HtmlChunk.br(), HtmlChunk.text("($pageName)")).toString()
      }
      else {
        tooltipText = attrLabel
        labelText = div.children(HtmlChunk.text(attrLabel), HtmlChunk.br(), HtmlChunk.nbsp()).toString()
      }

      myInheritanceLabel.isVisible = true
      myInheritanceLabel.text = labelText
      myInheritanceLabel.caret.dot = 0
      myInheritanceLabel.toolTipText = tooltipText
      myInheritanceLabel.isEnabled = true
      myInheritAttributesBox.isVisible = true
      myInheritAttributesBox.isEnabled = description.isEditable
      myInheritAttributesBox.isSelected = description.isInherited
      setEditEnabled(!description.isInherited && description.isEditable, description)
    }
    else {
      myInheritanceLabel.isVisible = false
      myInheritAttributesBox.isSelected = false
      myInheritAttributesBox.isVisible = false
      setEditEnabled(description.isEditable, description)
    }
  }

  private fun setEditEnabled(isEditEnabled: Boolean, description: ColorAndFontDescription) {
    myCbBackground.isEnabled = isEditEnabled && description.isBackgroundEnabled
    myCbForeground.isEnabled = isEditEnabled && description.isForegroundEnabled
    myCbBold.isEnabled = isEditEnabled && description.isFontEnabled
    myCbItalic.isEnabled = isEditEnabled && description.isFontEnabled
    myCbEffects.isEnabled = isEditEnabled && description.isEffectsColorEnabled
    myCbErrorStripe.isEnabled = isEditEnabled && description.isErrorStripeEnabled
    myErrorStripeColorChooser.setEditable(isEditEnabled)
    myEffectsColorChooser.setEditable(isEditEnabled)
    myForegroundChooser.setEditable(isEditEnabled)
    myBackgroundChooser.setEditable(isEditEnabled)
  }

  override fun apply(attrDescription: EditorSchemeAttributeDescriptor, scheme: EditorColorsScheme?) {
    val description = attrDescription as? ColorAndFontDescription ?: return

    description.isInherited = myInheritAttributesBox.isSelected
    if (description.isInherited) {
      val baseAttributes = description.baseAttributes
      if (baseAttributes != null) {
        description.fontType = baseAttributes.fontType
        description.isForegroundChecked = baseAttributes.foregroundColor != null
        description.foregroundColor = baseAttributes.foregroundColor
        description.isBackgroundChecked = baseAttributes.backgroundColor != null
        description.backgroundColor = baseAttributes.backgroundColor
        description.isErrorStripeChecked = baseAttributes.errorStripeColor != null
        description.errorStripeColor = baseAttributes.errorStripeColor
        description.effectColor = baseAttributes.effectColor
        description.effectType = baseAttributes.effectType
        description.isEffectsColorChecked = baseAttributes.effectColor != null
      }
      else {
        description.isInherited = false
      }
      reset(description)
    }
    else {
      setInheritanceInfo(description)
      var fontType = Font.PLAIN
      if (myCbBold.isSelected) fontType = fontType or Font.BOLD
      if (myCbItalic.isSelected) fontType = fontType or Font.ITALIC
      description.fontType = fontType
      description.isForegroundChecked = myCbForeground.isSelected
      description.foregroundColor = myForegroundChooser.selectedColor
      description.isBackgroundChecked = myCbBackground.isSelected
      description.backgroundColor = myBackgroundChooser.selectedColor
      description.isErrorStripeChecked = myCbErrorStripe.isSelected
      description.errorStripeColor = myErrorStripeColorChooser.selectedColor
      description.isEffectsColorChecked = myCbEffects.isSelected
      description.effectColor = myEffectsColorChooser.selectedColor

      if (myEffectsCombo.isEnabled) {
        val effectType = myEffectsCombo.model.selectedItem as String?
        description.effectType = effectType?.let { myEffectsMap[it] }
      }
    }
    description.apply(scheme)
  }

  override fun addListener(listener: OptionsPanelImpl.ColorDescriptionPanel.Listener) {
    myDispatcher.addListener(listener)
  }
}

private val MODIFIED_ITEM_FOREGROUND: Color = JBColor.namedColor("Tree.modifiedItemForeground", JBColor.BLUE)

/** WCAG minimum contrast ratio for normal text, see [ColorUtil.getContrast]. */
private const val MIN_CONTRAST_RATIO = 4.5

private const val CONTRAST_WARNING_WRAP_LENGTH = 25

/**
 * Checks the colors the option is actually rendered with: a missing foreground or background falls back
 * to the scheme's default text colors, and semi-transparent colors are composited over the background.
 * Not checked: options that define neither foreground nor background (they only show the surrounding text),
 * and background-only options whose foreground channel is not applicable at all — such backgrounds are used
 * for non-text painting like gutter stripes, so there is no foreground to contrast them with.
 */
private fun getContrastWarningText(description: ColorAndFontDescription?): @Nls String? {
  if (description == null) return null
  val foreground = description.externalForeground
  val background = description.externalBackground
  if (foreground == null && background == null) return null
  if (foreground == null && !description.isForegroundEnabled) return null

  val scheme = description.scheme
  val defaultBackground = scheme?.defaultBackground
  val effectiveBackground = when {
    background == null -> defaultBackground
    defaultBackground != null -> ColorUtil.alphaBlending(background, defaultBackground)
    else -> background
  }
  val effectiveForeground = when {
    foreground == null -> scheme?.defaultForeground
    effectiveBackground != null -> ColorUtil.alphaBlending(foreground, effectiveBackground)
    else -> foreground
  }
  if (effectiveForeground == null || effectiveBackground == null) return null

  val contrast = ColorUtil.getContrast(effectiveForeground, effectiveBackground)
  if (contrast >= MIN_CONTRAST_RATIO) return null
  return ApplicationBundle.message("color.option.low.contrast.warning", String.format(Locale.ROOT, "%.1f", contrast))
}

/**
 * A fixed-size slot with a revert icon button inside.
 * The slot always occupies its space, so showing or hiding the button doesn't shift the surrounding layout.
 */
private class RevertButton(@NlsActions.ActionText text: String, onRevert: () -> Unit) : JPanel(BorderLayout()) {
  private val myButton: ActionButton

  init {
    isOpaque = false
    val action = object : DumbAwareAction(text, null, AllIcons.Diff.Revert) {
      override fun actionPerformed(e: AnActionEvent) {
        onRevert()
      }
    }
    val size = AllIcons.Diff.Revert.iconHeight + 4 // add space for the hover border
    myButton = ActionButton(action, action.templatePresentation.clone(), ActionPlaces.UNKNOWN, Dimension(size, size))
    myButton.isVisible = false
    add(myButton, BorderLayout.CENTER)
    preferredSize = Dimension(size, size)
    minimumSize = preferredSize
    // the button is a little higher than a checkbox row, don't let it add vertical gaps
    putClientProperty(DslComponentProperty.VERTICAL_COMPONENT_GAP, VerticalComponentGap.NONE)
  }

  fun setRevertAvailable(available: Boolean) {
    myButton.isVisible = available
  }
}

private fun updateColorChooser(checkBox: JCheckBox,
                               colorPanel: ColorPanel,
                               isEnabled: Boolean,
                               isChecked: Boolean,
                               color: Color?,
                               supportTransparency: Boolean = false) {
  checkBox.isEnabled = isEnabled
  checkBox.isSelected = isChecked
  if (color != null) {
    colorPanel.selectedColor = color
  }
  else {
    colorPanel.selectedColor = JBColor.WHITE
  }
  colorPanel.setSupportTransparency(supportTransparency)
  colorPanel.isEnabled = isChecked
}
