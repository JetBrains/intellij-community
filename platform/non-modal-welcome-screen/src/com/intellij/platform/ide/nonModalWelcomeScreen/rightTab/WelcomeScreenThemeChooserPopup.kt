// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab

import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafReference
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.ui.popup.PopupChooserBuilder
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.Disposer
import com.intellij.platform.ide.nonModalWelcomeScreen.NonModalWelcomeScreenBundle
import com.intellij.platform.ide.nonModalWelcomeScreen.WelcomeScreenComboBoxKind
import com.intellij.platform.ide.nonModalWelcomeScreen.WelcomeScreenTabUsageCollector
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeScreenRightTabComboBoxModel.ThemeModel
import com.intellij.ui.CollectionListModel
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.IntelliJSpacingConfiguration
import com.intellij.ui.dsl.builder.SegmentedButton
import com.intellij.ui.dsl.builder.components.SegmentedButtonComponent
import com.intellij.ui.dsl.builder.components.SegmentedButtonComponent.Companion.whenItemSelected
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants
import javax.swing.Timer
import javax.swing.UIManager

/** Same debounce the platform's theme and IDE-scale quick-switchers use for apply-on-highlight. */
private const val PREVIEW_THROTTLING_MS = 500

private const val VISIBLE_ROW_COUNT = 12

private const val PREVIEW_WIDTH = 420
private const val PREVIEW_HEIGHT = 260

/**
 * Theme chooser for the welcome screen's Theme control: a list that previews the highlighted theme, with a
 * Light/Dark switch in its header.
 *
 * Deliberately a standalone [JBPopup] rather than the combobox's own popup, for two reasons that both come
 * from a `ComboPopup` being owned by its combobox:
 * - it hides itself from `AncestorListener.ancestorMoved`, and applying a theme relayouts the frame, which
 *   would cancel the popup mid-navigation and revert the preview;
 * - a `ListPopupImpl` has no slot for an interactive header — its caption is forced to zero height when the
 *   step has no title, and the header slot is taken by speed search.
 */
internal class WelcomeScreenThemeChooserPopup(
  private val model: ThemeModel,
  private val previewSample: WelcomeRightTabContentProvider.ThemePreviewSample?,
) {
  /**
   * Builds the popup; the caller shows it, which is what decides where it appears.
   *
   * [onDispose] comes from the trigger button and has to run on every close path. The button passes null
   * when it shows the popup itself, so there is nothing to release.
   */
  fun createPopup(owner: JComponent, onDispose: Runnable?): JBPopup {
    val previewer = model.previewer
    val snapshot = previewer.capture()

    val listModel = CollectionListModel(model.items)
    val list = JBList(listModel)
    list.selectionMode = ListSelectionModel.SINGLE_SELECTION
    list.visibleRowCount = VISIBLE_ROW_COUNT

    // Mirrors ComboBoxPopup.configureList: combobox selection colours, and padding on the list rather
    // than on the cells, because the Kotlin UI DSL renderer brings its own item insets.
    list.selectionForeground = UIManager.getColor("ComboBox.selectionForeground")
    list.selectionBackground = UIManager.getColor("ComboBox.selectionBackground")
    list.border = JBUI.Borders.empty(PopupUtil.getListInsets(false, false))

    list.setSelectedValue(model.currentItem, true)

    // Debounced apply-on-highlight, so holding an arrow key or sweeping the mouse does not thrash the LAF.
    val debounce = Timer(PREVIEW_THROTTLING_MS) {
      val index = list.selectedIndex
      if (index >= 0) {
        previewer.preview(index)
      }
    }
    debounce.isRepeats = false

    list.addListSelectionListener { event ->
      if (!event.valueIsAdjusting && list.selectedIndex >= 0) {
        debounce.restart()
      }
    }

    // PopupChooserBuilder rather than a hand-assembled component popup: it brings the platform's own
    // list-popup behaviour, including selection following the mouse and choosing on click or Enter, and it
    // is the only builder with a north slot for the brightness switch.
    val popupDisposable = Disposer.newDisposable("WelcomeScreenThemeChooserPopup")

    val builder = PopupChooserBuilder(list)
    // The renderer the Appearance settings combobox uses, so rows match the platform's theme popup:
    // names, a Beta badge and restart hint where a theme needs one, and group separators.
    builder.setRenderer(LafManager.getInstance().getLookAndFeelCellRenderer(list))
    builder.setNorthComponent(createBrightnessSwitch(listModel, list))
    previewSample?.let { builder.setEastComponent(createCodePreview(it, popupDisposable)) }
    builder.setAutoselectOnMouseMove(true)
    builder.setRequestFocus(true)
    builder.setCancelOnClickOutside(true)
    builder.setCancelKeyEnabled(true)
    builder.setResizable(false)
    builder.setMovable(false)
    builder.setMinSize(Dimension(owner.width, 0))
    // PopupChooserBuilder wires this as the popup's OK handler, so it runs after the popup has closed.
    builder.setItemChosenCallback(Runnable {
      val selected = list.selectedValue ?: return@Runnable
      model.commit(selected)
      WelcomeScreenTabUsageCollector.logComboBoxValueChanged(WelcomeScreenComboBoxKind.THEME)
    })

    val popup = builder.createPopup()
    Disposer.register(popup, popupDisposable)

    // The list column is sized by the popup, not by the longest theme name, so a horizontal scrollbar
    // would only cut the rows. The builder creates its scroll pane inside createPopup, hence the order.
    builder.scrollPane?.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER

    popup.addListener(object : JBPopupListener {
      override fun onClosed(event: LightweightWindowEvent) {
        debounce.stop()
        // isOk means the user chose an item, and the choice is applied by the OK handler above, which
        // runs after this. A flag of our own would still be unset here and would revert the choice.
        if (!event.isOk) {
          previewer.restore(snapshot)
        }
        onDispose?.run()
      }
    })

    return popup
  }

  /**
   * Read-only editor beside the list. It needs no project and no backend: the highlighter comes from the
   * file type alone, so it is lexer-based.
   *
   * The editor follows [EditorColorsManager.TOPIC] rather than being redrawn by the chooser, because a
   * preview applies the theme for real and that is what changes the global scheme.
   */
  private fun createCodePreview(sample: WelcomeRightTabContentProvider.ThemePreviewSample, parent: Disposable): JComponent {
    val editorFactory = EditorFactory.getInstance()
    val document = editorFactory.createDocument(sample.text)
    val editor = editorFactory.createViewer(document, null, EditorKind.PREVIEW) as EditorEx
    Disposer.register(parent) { editorFactory.releaseEditor(editor) }

    editor.setCaretEnabled(false)
    // Soft wraps keep every line inside the narrow preview, so nothing is lost off the right edge. The
    // vertical scrollbar then reaches the rest of the sample, which wrapping makes taller.
    editor.setHorizontalScrollbarVisible(false)
    editor.setVerticalScrollbarVisible(true)
    editor.settings.apply {
      isUseSoftWraps = true
      isPaintSoftWraps = false
      isLineNumbersShown = false
      isLineMarkerAreaShown = false
      isFoldingOutlineShown = false
      isRightMarginShown = false
      isCaretRowShown = false
      isIndentGuidesShown = false
      additionalLinesCount = 0
      additionalColumnsCount = 0
    }

    fun applyGlobalScheme() {
      val scheme = EditorColorsManager.getInstance().globalScheme
      editor.colorsScheme = scheme
      editor.highlighter = EditorHighlighterFactory.getInstance()
        .createEditorHighlighter(scheme, sample.fileName, null)
      editor.backgroundColor = scheme.defaultBackground
    }

    applyGlobalScheme()
    ApplicationManager.getApplication().messageBus.connect(parent)
      .subscribe(EditorColorsManager.TOPIC, EditorColorsListener { applyGlobalScheme() })

    editor.component.preferredSize = Dimension(JBUI.scale(PREVIEW_WIDTH), JBUI.scale(PREVIEW_HEIGHT))
    return editor.component
  }

  /**
   * Light/Dark switch in the popup header. Narrowing the list re-previews, which is harmless here because
   * everything in this popup is a preview until the user commits.
   */
  private fun createBrightnessSwitch(listModel: CollectionListModel<LafReference>, list: JBList<LafReference>): JComponent {
    val switch = SegmentedButtonComponent<ThemeBrightnessFilter> { filter ->
      val textKey = when (filter) {
        ThemeBrightnessFilter.LIGHT -> "welcome.screen.right.tab.theme.brightness.light"
        ThemeBrightnessFilter.DARK -> "welcome.screen.right.tab.theme.brightness.dark"
      }
      SegmentedButton.createPresentation(text = NonModalWelcomeScreenBundle.message(textKey))
    }
    switch.spacing = IntelliJSpacingConfiguration()
    switch.items = listOf(ThemeBrightnessFilter.LIGHT, ThemeBrightnessFilter.DARK)
    switch.accessibleContext.accessibleName =
      NonModalWelcomeScreenBundle.message("welcome.screen.right.tab.theme.brightness.accessible.name")
    switch.border = JBUI.Borders.empty(8, 8, 4, 8)

    // Always open on the active theme's family rather than whatever the last popup left behind, so the
    // list never opens without the current theme in it. Seeded before subscribing, so this initial state
    // is not taken for a user action.
    val initialFilter = model.currentBrightness()
    model.brightnessFilter = initialFilter
    switch.selectedItem = initialFilter

    switch.whenItemSelected { filter ->
      model.brightnessFilter = filter
      val items = model.items
      listModel.replaceAll(items)
      // Keep the active theme selected when the new family still contains it, otherwise start at the top.
      val activeIndex = items.indexOf(model.currentItem)
      list.selectedIndex = if (activeIndex >= 0) activeIndex else 0
      list.ensureIndexIsVisible(list.selectedIndex)
    }

    return switch
  }
}
