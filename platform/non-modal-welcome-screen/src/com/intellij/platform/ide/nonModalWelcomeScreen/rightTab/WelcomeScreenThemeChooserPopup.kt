// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab

import com.intellij.application.options.colors.highlighting.HighlightData
import com.intellij.application.options.colors.highlighting.HighlightsExtractor
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.ide.ui.LafReference
import com.intellij.ide.ui.ThemeListProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.PopupChooserBuilder
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import com.intellij.platform.ide.nonModalWelcomeScreen.NonModalWelcomeScreenBundle
import com.intellij.platform.ide.nonModalWelcomeScreen.WelcomeScreenComboBoxKind
import com.intellij.platform.ide.nonModalWelcomeScreen.WelcomeScreenTabUsageCollector
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeScreenRightTabComboBoxModel.ThemeModel
import com.intellij.ui.CollectionListModel
import com.intellij.ui.components.Badge
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.IntelliJSpacingConfiguration
import com.intellij.ui.dsl.builder.SegmentedButton
import com.intellij.ui.dsl.builder.components.SegmentedButtonComponent
import com.intellij.ui.dsl.builder.components.SegmentedButtonComponent.Companion.whenItemSelected
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants
import javax.swing.Timer
import javax.swing.UIManager

/** Same debounce the platform's theme and IDE-scale quick-switchers use for apply-on-highlight. */
private const val PREVIEW_THROTTLING_MS = 500

private const val VISIBLE_ROW_COUNT = 10

/**
 * Height of a plain row, unscaled. It is the row height of the new UI themes. The rows must not depend on the
 * look and feel, because the old Darcula theme has a row height of 20 and the popup then looks lower.
 */
private const val ROW_HEIGHT = 24

/** Upper bound of the list width, counted in characters. A longer theme name is shortened with an ellipsis. */
private const val MAX_NAME_CHARS = 50

private const val PREVIEW_WIDTH = 360
private const val PREVIEW_HEIGHT = 230

/** Unscaled gap above and below the text of a Light/Dark segment. See [WelcomeScreenThemeChooserPopup.createBrightnessSwitch]. */
private const val SWITCH_VERTICAL_GAP = 5

/** Distance from the left edge of the sample editor to its text. */
private const val PREVIEW_TITLE_INDENT = 10

/** The sample uses a slightly smaller font than the editor, so more of each line fits the narrow column. */
private const val PREVIEW_FONT_SCALE = 0.9f

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

    // Open on the active theme's family, rather than on whatever the last popup left behind. This has to
    // happen before the list model is built, because that reads model.items once.
    val initialFilter = model.currentBrightness()
    model.brightnessFilter = initialFilter

    // An empty theme id makes the platform renderer draw a separator above the row. The row opens the
    // Appearance settings and is not a theme, so the previewer never sees it: its index is past the themes.
    val moreItem = LafReference(NonModalWelcomeScreenBundle.message("welcome.screen.right.tab.theme.more.in.appearance"), "")
    fun listItems() = model.items + moreItem

    val listModel = CollectionListModel(listItems())
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
    // Rows look like the ones in the platform's theme popup: names, a Beta badge, and group separators.
    builder.setRenderer(createUniformRenderer(list, moreItem))
    builder.setNorthComponent(createHeader(createBrightnessSwitch(initialFilter, listModel, list, ::listItems)))
    previewSample?.let { builder.setEastComponent(createCodePreview(it, popupDisposable)) }
    builder.setAutoselectOnMouseMove(true)
    builder.setRequestFocus(true)
    builder.setCancelOnClickOutside(true)
    builder.setCancelKeyEnabled(true)
    builder.setResizable(false)
    builder.setMovable(false)
    builder.setMinSize(Dimension(owner.width, 0))
    builder.setCloseOnEnter(false)

    var committed = false
    var finished = false
    var popupRef: JBPopup? = null

    builder.setItemChosenCallback(Runnable {
      val selected = list.selectedValue ?: return@Runnable
      if (selected === moreItem) {
        // Closing without a choice restores the theme the user had before the preview.
        popupRef?.cancel()
        ApplicationManager.getApplication().invokeLater {
          ShowSettingsUtilImpl.showSettingsDialog(null, "preferences.lookFeel", null)
        }
        return@Runnable
      }
      committed = true
      debounce.stop()
      model.commit(selected)
      WelcomeScreenTabUsageCollector.logComboBoxValueChanged(WelcomeScreenComboBoxKind.THEME)
      popupRef?.cancel()
    })

    builder.setCancelCallback(Computable {
      if (!finished) {
        finished = true
        debounce.stop()
        if (!committed) {
          previewer.restore(snapshot)
        }
        onDispose?.run()
      }
      true
    })

    val popup = builder.createPopup()
    popupRef = popup
    Disposer.register(popup, popupDisposable)

    // Both of these need the scroll pane, which the builder creates inside createPopup, hence the order.
    builder.scrollPane?.let { scrollPane ->
      // The popup sizes the list column, not the longest theme name, so a horizontal scrollbar would
      // only cut the rows.
      scrollPane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
      // visibleRowCount does not bind here, because the builder takes the height from the content. Cap
      // the list at the height of the sample beside it, so the two columns match and the popup stays small.
      scrollPane.preferredSize = Dimension(scrollPane.preferredSize.width, JBUI.scale(PREVIEW_HEIGHT))
    }

    return popup
  }

  /**
   * Renderer whose rows do not change size with the theme or with the theme family.
   *
   * By default a row is as high as the `List.rowHeight` of the active look and feel, and the list is as wide as
   * its widest name. Both change with the theme and with the Light/Dark switch, and the popup then jumps. Here
   * every row is [ROW_HEIGHT] high, separator not counted, and has one width: the widest row of either family,
   * capped at [MAX_NAME_CHARS] characters. The renderer draws a name that does not fit with an ellipsis.
   */
  private fun createUniformRenderer(list: JBList<LafReference>, moreItem: LafReference): ListCellRenderer<LafReference> {
    val groups = ThemeListProvider.getInstance().getShownThemes().infos
    fun isBeta(themeId: String) = groups.any { group -> group.items.any { it.id == themeId && it.isRestartRequired() } }

    var fixedRowWidth: Int? = null

    // The content is the one that LafManager.getLookAndFeelCellRenderer draws on the welcome screen: the name,
    // a Beta badge, and a separator above the first theme of a group. That renderer does not let a caller set
    // the row size, so the same rows are drawn here.
    val renderer = listCellRenderer<LafReference> {
      toolTipText = null
      text(value.name)
      if (value.themeId.isEmpty()) {
        separator { }
      }
      else {
        if (isBeta(value.themeId)) {
          icon(Badge.beta)
        }
        groups.firstOrNull { value.themeId == it.items.firstOrNull()?.id }?.let { group ->
          separator { text = group.title }
        }
      }
      rowHeight = JBUI.scale(ROW_HEIGHT)
      rowWidth = fixedRowWidth
    }

    val widestRow = (model.allItems() + moreItem).maxOfOrNull {
      renderer.getListCellRendererComponent(list, it, -1, false, false).preferredSize.width
    } ?: 0
    fixedRowWidth = minOf(widestRow, list.getFontMetrics(list.font).charWidth('0') * MAX_NAME_CHARS)
    return renderer
  }

  /**
   * Read-only editor beside the list. It needs no project and no backend. The highlighter comes from the
   * file type, so it is lexer-based, and the sample declares the rest of its colours through tags.
   *
   * The editor follows [EditorColorsManager.TOPIC] rather than being redrawn by the chooser, because a
   * preview applies the theme for real and that is what changes the global scheme.
   */
  private fun createCodePreview(sample: WelcomeRightTabContentProvider.ThemePreviewSample, parent: Disposable): JComponent {
    val highlighterFactory = EditorHighlighterFactory.getInstance()

    // Building a highlighter for the file name first loads the language support, which is what registers
    // the attribute keys the tags name below. Resolving a name earlier would hand back a bare key,
    // without the fallback the language gives it, and nothing would paint it.
    highlighterFactory.createEditorHighlighter(EditorColorsManager.getInstance().globalScheme, sample.fileName, null)

    val tags = sample.highlightTags.mapValues { TextAttributesKey.createTextAttributesKey(it.value) }
    val declaredHighlights = mutableListOf<HighlightData>()
    val text = HighlightsExtractor(tags).extractHighlights(sample.text, declaredHighlights)

    val editorFactory = EditorFactory.getInstance()
    val document = editorFactory.createDocument(text)
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
      // Assigning a scheme resets the font size, so the reduction has to follow every assignment.
      editor.setFontSize(scheme.editorFontSize2D * PREVIEW_FONT_SCALE)
      editor.highlighter = highlighterFactory.createEditorHighlighter(scheme, sample.fileName, null)
      editor.backgroundColor = scheme.defaultBackground
    }

    applyGlobalScheme()

    // Added once, not per scheme: each highlighter carries the scheme key rather than resolved colours,
    // so it repaints itself when the theme changes. Each one also lays an erase marker over the lexer's
    // own attributes underneath, which is what lets a declared colour win.
    for (highlight in declaredHighlights) {
      highlight.addHighlToView(editor, EditorColorsManager.getInstance().globalScheme, emptyMap())
    }
    ApplicationManager.getApplication().messageBus.connect(parent)
      .subscribe(EditorColorsManager.TOPIC, EditorColorsListener { applyGlobalScheme() })

    // The size goes on a wrapper that reports it from getPreferredSize. An editor recomputes the
    // preferred size of its own component from its content, so setting it there does not hold.
    val wrapper = object : JPanel(BorderLayout()) {
      override fun getPreferredSize(): Dimension =
        Dimension(JBUI.scale(PREVIEW_WIDTH), JBUI.scale(PREVIEW_HEIGHT))
    }
    wrapper.isOpaque = false
    wrapper.add(editor.component, BorderLayout.CENTER)
    return wrapper
  }

  /**
   * Light/Dark switch in the popup header. Narrowing the list re-previews, which is harmless here because
   * everything in this popup is a preview until the user commits.
   */
  private fun createBrightnessSwitch(
    initialFilter: ThemeBrightnessFilter,
    listModel: CollectionListModel<LafReference>,
    list: JBList<LafReference>,
    listItems: () -> List<LafReference>,
  ): JComponent {
    val switch = SegmentedButtonComponent<ThemeBrightnessFilter> { filter ->
      val textKey = when (filter) {
        ThemeBrightnessFilter.LIGHT -> "welcome.screen.right.tab.theme.brightness.light"
        ThemeBrightnessFilter.DARK -> "welcome.screen.right.tab.theme.brightness.dark"
      }
      SegmentedButton.createPresentation(text = NonModalWelcomeScreenBundle.message(textKey))
    }
    // A segment is as high as the larger of its text plus these gaps and `Button.minimumSize` less 2, and the
    // minimum depends on the theme. The gaps are large enough to always win, so the switch keeps its height.
    switch.spacing = object : IntelliJSpacingConfiguration() {
      override val segmentedButtonVerticalGap: Int = SWITCH_VERTICAL_GAP
    }
    switch.items = listOf(ThemeBrightnessFilter.DARK, ThemeBrightnessFilter.LIGHT)
    switch.accessibleContext.accessibleName =
      NonModalWelcomeScreenBundle.message("welcome.screen.right.tab.theme.brightness.accessible.name")

    // The caller already put this filter on the model. Set it before subscribing, so the initial state
    // is not taken for a user action.
    switch.selectedItem = initialFilter

    switch.whenItemSelected { filter ->
      model.brightnessFilter = filter
      listModel.replaceAll(listItems())
      // Keep the active theme selected when the new family still contains it, otherwise start at the top.
      val activeIndex = model.items.indexOf(model.currentItem)
      list.selectedIndex = if (activeIndex >= 0) activeIndex else 0
      list.ensureIndexIsVisible(list.selectedIndex)
    }

    return switch
  }

  /**
   * The popup header: the switch on the left, and over the code sample the title of the sample.
   *
   * The title box is as wide as the sample, so the two share their left edge. BorderLayout gives the
   * switch its own preferred width, which keeps it narrow instead of stretching it over the popup.
   */
  private fun createHeader(switch: JComponent): JComponent {
    val header = JPanel(BorderLayout())
    header.isOpaque = false
    header.border = JBUI.Borders.empty(8, 8, 4, 8)
    header.add(switch, BorderLayout.WEST)

    if (previewSample != null) {
      val title = JBLabel(NonModalWelcomeScreenBundle.message("welcome.screen.right.tab.theme.preview.title"))
      title.foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
      // The editor pads its text from its own edge, so the title takes the same indent to start over the code.
      title.border = JBUI.Borders.emptyLeft(PREVIEW_TITLE_INDENT)
      val titleBox = object : JPanel(BorderLayout()) {
        override fun getPreferredSize(): Dimension = Dimension(JBUI.scale(PREVIEW_WIDTH), super.getPreferredSize().height)
      }
      titleBox.isOpaque = false
      titleBox.add(title, BorderLayout.WEST)
      header.add(titleBox, BorderLayout.EAST)
    }
    return header
  }
}
