// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.regexp.inspection.custom

import com.intellij.find.FindBundle
import com.intellij.find.FindModel
import com.intellij.find.impl.FindInProjectUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.ui.*
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.util.ui.JBUI
import org.intellij.lang.regexp.RegExpBundle
import org.intellij.lang.regexp.RegExpFileType
import org.intellij.lang.regexp.inspection.custom.RegExpInspectionConfiguration.InspectionPattern
import org.intellij.lang.regexp.inspection.custom.RegExpInspectionConfiguration.RegExpFlags
import java.awt.Dimension
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.border.CompoundBorder

class RegExpDialog(val project: Project?, val editConfiguration: Boolean, defaultPattern: InspectionPattern? = null) : DialogWrapper(project, true) {
  private var searchContext: FindModel.SearchContext = FindModel.SearchContext.ANY
  private var flags: Int = 0
  private var replace: Boolean = false
    set(value) {
      field = value
      replaceLabel.isEnabled = value
      replaceRow.enabled(value)
      replaceButton.text = when (value) {
        true -> RegExpBundle.message("button.search.only")
        false -> RegExpBundle.message("button.enable.replace")
      }
      if (!editConfiguration) {
        setOKButtonText(when (value) {
                          true -> FindBundle.message("find.replace.command")
                          false -> FindBundle.message("find.dialog.find.button")
                        })
      }
    }
  private var replaceEditorFocusedLast = false

  private lateinit var fileCombo: ComboBox<FileType>
  private lateinit var filterButton: ActionButton
  private lateinit var flagsButton: ActionButton
  private lateinit var searchEditor: EditorTextField
  private lateinit var replaceLabel: JLabel
  private lateinit var replaceButton: JButton
  private lateinit var replaceRow: Row
  private lateinit var replaceEditor: EditorTextField
  private lateinit var splitter: OnePixelSplitter

  val pattern: InspectionPattern
    get() = InspectionPattern(
      searchEditor.text,
      fileCombo.item,
      flags,
      searchContext,
      if (replace) replaceEditor.text else null
    )

  init {
    title = RegExpBundle.message("regexp.dialog.title")
    init()
    if (!editConfiguration) {
      isModal = false
    }

    defaultPattern?.let { pattern ->
      searchEditor.text = pattern.regExp
      flags = pattern.flags
      searchContext = pattern.searchContext
      fileCombo.item = pattern.fileType() ?: UnknownFileType.INSTANCE
      pattern.replacement?.let { replaceEditor.text = it }
    }
    replace = defaultPattern?.replacement != null
  }

  private fun createEditorsPanel(): JComponent = panel {
    val intelliJSpacingConfiguration = IntelliJSpacingConfiguration()

    panel {
      row {
        @Suppress("DialogTitleCapitalization")
        label(RegExpBundle.message("regexp.dialog.search.template"))
          .resizableColumn()
          .align(AlignX.FILL)
        val fileTypes = mutableListOf(UnknownFileType.INSTANCE)
        fileTypes.addAll(
          FileTypeManager.getInstance().registeredFileTypes.filterNotNull()
            .sortedBy { it.displayName }
            .filter { it != UnknownFileType.INSTANCE }
        )
        fileCombo = comboBox(fileTypes, SimpleTypeRenderer())
          .label(RegExpBundle.message("regexp.dialog.language"))
          .applyToComponent {
            preferredSize.width = 150
            isSwingPopup = false
          }
          .gap(RightGap.SMALL)
          .component
        filterButton = actionButton(MyFilterAction())
          .gap(RightGap.SMALL)
          .component
        flagsButton = actionButton(SelectRegExpFlagsAction())
          .component
      }
    }.customize(UnscaledGaps(0, intelliJSpacingConfiguration.horizontalSmallGap, 0, intelliJSpacingConfiguration.horizontalSmallGap))

    row {
      searchEditor = cell(createEditor(true))
        .resizableColumn()
        .align(Align.FILL)
        .applyToComponent { addFocusListener(object : FocusAdapter() {
          override fun focusGained(e: FocusEvent?) {
            replaceEditorFocusedLast = false
          }
        }) }
        .component
    }.resizableRow()

    panel {
      row {
        replaceLabel = label(RegExpBundle.message("regexp.dialog.replace.template"))
          .resizableColumn()
          .align(AlignX.FILL)
          .component
        replaceButton = button(if (replace) RegExpBundle.message("button.search.only") else RegExpBundle.message("button.enable.replace")) {
          replace = !replace
        }.component
      }
    }.customize(UnscaledGaps(10, intelliJSpacingConfiguration.horizontalSmallGap, 0, intelliJSpacingConfiguration.horizontalSmallGap))

    replaceRow = row {
      replaceEditor = cell(createEditor(false))
        .resizableColumn()
        .align(Align.FILL)
        .applyToComponent { addFocusListener(object : FocusAdapter() {
          override fun focusGained(e: FocusEvent?) {
            replaceEditorFocusedLast = true
          }
        }) }
        .customize(UnscaledGaps(intelliJSpacingConfiguration.verticalComponentGap, 0, 0, 0))
        .component
    }.resizableRow()
  }

  override fun createCenterPanel(): JComponent {
    splitter = OnePixelSplitter()
    splitter.firstComponent = RegExpSampleTree { insertSample(it) }.panel
    splitter.secondComponent = createEditorsPanel()
    searchEditor.grabFocus()
    return splitter
}

  private fun insertSample(sample: RegExpSample) {
    val editor = if (replaceEditorFocusedLast) replaceEditor else searchEditor
    val caret = editor.caretModel.allCarets.firstOrNull() ?: return
    val insertOffset = caret.offset
    val sampleOffset = if (sample.caretOffset == -1) sample.sample.length else sample.caretOffset
    when (caret.hasSelection()) {
      true -> {
        val selection = caret.selectedText!!
        val start = editor.text.dropLast(editor.text.length - caret.selectionStart)
        val end = editor.text.drop(caret.selectionEnd)
        val sampleStart = sample.sample.dropLast(sample.sample.length - sampleOffset)
        val sampleEnd = sample.sample.drop(sampleOffset)
        editor.text = start + sampleStart + selection + sampleEnd + end
        caret.moveToOffset(caret.selectionStart + sampleStart.length + selection.length)
      }
      false -> {
        val start = editor.text.dropLast(editor.text.length - insertOffset)
        val end = editor.text.drop(insertOffset)
        editor.text = start + sample.sample + end
        caret.moveToOffset(caret.offset + sampleOffset)
      }
    }
    editor.grabFocus()
  }

  override fun getStyle(): DialogStyle = DialogStyle.COMPACT

  fun createEditor(search: Boolean): EditorTextField {
    val document = EditorFactory.getInstance().createDocument("")
    return MyEditorTextField(document, search).apply {
      font = EditorFontType.getGlobalPlainFont()
      preferredSize = Dimension(550, 100)
    }
  }

  private inner class MyEditorTextField(document: Document, val search: Boolean) 
    : EditorTextField(document, project, if (search) RegExpFileType.INSTANCE else PlainTextFileType.INSTANCE, false, false) {
    override fun createEditor(): EditorEx {
      return super.createEditor().apply {
        setHorizontalScrollbarVisible(true)
        setVerticalScrollbarVisible(true)
        backgroundColor = EditorColorsManager.getInstance().getGlobalScheme().defaultBackground
        val outerBorder = JBUI.Borders.customLine(JBColor.border(), 1, 0, if (search) 1 else 0, 0)
        scrollPane.border = CompoundBorder(
          outerBorder,
          JBUI.Borders.empty(6, 8)
        )
        isEmbeddedIntoDialogWrapper = true
      }
    }
  }

  inner class SimpleTypeRenderer : SimpleListCellRenderer<FileType?>() {
    override fun customize(list: JList<out FileType?>, value: FileType?, index: Int, selected: Boolean, hasFocus: Boolean) {
      if (value == null) return
      when (value) {
        UnknownFileType.INSTANCE -> {
          text = RegExpBundle.message("label.any")
          icon = AllIcons.FileTypes.Any_type
        }
        else -> {
          text = value.displayName
          icon = value.icon ?: AllIcons.FileTypes.Text
        }
      }
    }
  }

  private inner class MyFilterAction : DumbAwareAction(
    FindBundle.messagePointer("find.popup.show.filter.popup"),
    Presentation.NULL_STRING,
    LayeredIcon.create(AllIcons.General.Filter, AllIcons.General.Dropdown)
  ) {
    val myGroup: ActionGroup
    var listPopup: ListPopup? = null
    init {
      ActionManager.getInstance().getKeyboardShortcut("ShowFilterPopup")?.let {
        shortcutSet = CustomShortcutSet(it)
      }
      myGroup = DefaultActionGroup().apply {
        FindModel.SearchContext.entries.forEach { add(MyToggleAction(it, this@MyFilterAction)) }
        isPopup = true
      }
    }

    override fun actionPerformed(e: AnActionEvent) {
      listPopup = JBPopupFactory.getInstance().createActionGroupPopup(null, myGroup, e.dataContext, false, null, 10)
      listPopup?.showUnderneathOf(filterButton)
    }
  }

  private inner class MyToggleAction(val context: FindModel.SearchContext, val action: MyFilterAction) : ToggleAction(FindInProjectUtil.getPresentableName(context)), DumbAware {
    override fun isSelected(e: AnActionEvent): Boolean {
      return searchContext == context
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.BGT
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      searchContext = context
      action.listPopup?.closeOk(null)
      filterButton.repaint()
    }
  }


  private inner class SelectRegExpFlagsAction : DumbAwareAction(RegExpBundle.messagePointer("regexp.dialog.regexp.flags"), Presentation.NULL_STRING, LayeredIcon.GEAR_WITH_DROPDOWN) {
    val myGroup: ActionGroup = DefaultActionGroup().apply {
      RegExpFlags.entries.forEach { add(ToggleFlagAction(it)) }
      isPopup = true
    }

    override fun actionPerformed(e: AnActionEvent) {
      JBPopupFactory.getInstance()
        .createActionGroupPopup(RegExpBundle.message("regexp.dialog.regexp.flags"), myGroup, e.dataContext, false, null, 10)
        .showUnderneathOf(filterButton)
    }
  }

  private inner class ToggleFlagAction(
    val flag: RegExpFlags,
  ) : ToggleAction(flag.text) {

    init {
      templatePresentation.putClientProperty(ActionUtil.SECONDARY_TEXT, flag.mnemonic?.toString())
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun isSelected(e: AnActionEvent): Boolean {
      return flags and flag.id != 0
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      if ((flags and flag.id != 0) == state) return
      flags = flags xor flag.id
    }
  }
}