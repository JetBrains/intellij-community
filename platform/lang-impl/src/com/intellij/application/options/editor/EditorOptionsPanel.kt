// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.editor

import com.intellij.accessibility.AccessibilityUtils
import com.intellij.application.options.editor.EditorCaretStopPolicyItem.*
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings
import com.intellij.codeInsight.daemon.impl.IdentifierHighlighterPass
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationBundle.message
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.actions.CaretStopBoundary
import com.intellij.openapi.editor.actions.CaretStopOptionsTransposed
import com.intellij.openapi.editor.actions.CaretStopOptionsTransposed.Companion.fromCaretStopOptions
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable.TOOLTIPS_DELAY_RANGE
import com.intellij.openapi.editor.richcopy.settings.RichCopySettings
import com.intellij.openapi.extensions.BaseExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.options.BoundCompositeConfigurable
import com.intellij.openapi.options.Configurable.WithEpDependencies
import com.intellij.openapi.options.Scheme
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.options.ex.ConfigurableWrapper
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.profile.codeInspection.ui.ErrorOptionsProvider
import com.intellij.profile.codeInspection.ui.ErrorOptionsProviderEP
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.layout.*
import org.jetbrains.annotations.Contract
import org.jetbrains.annotations.Nls
import javax.swing.DefaultComboBoxModel
import javax.swing.JCheckBox

private val codeInsightSettings
  get() = CodeInsightSettings.getInstance()
private val editorSettings
  get() = EditorSettingsExternalizable.getInstance()
private val stripTrailingSpacesProxy get() = StripTrailingSpacesProxy()

private val richCopySettings
  get() = RichCopySettings.getInstance()
private val codeAnalyzerSettings
  get() = DaemonCodeAnalyzerSettings.getInstance()

@Contract(pure = true)
private fun String.capitalizeWords(): String = StringUtil.capitalizeWords(this, true)

private val enableWheelFontChange
  get() = CheckboxDescriptor(if (SystemInfo.isMac) message("checkbox.enable.ctrl.mousewheel.changes.font.size.macos")
                             else message("checkbox.enable.ctrl.mousewheel.changes.font.size"),
                             PropertyBinding(editorSettings::isWheelFontChangeEnabled, editorSettings::setWheelFontChangeEnabled))

private val enableDnD
  get() = CheckboxDescriptor(message("checkbox.enable.drag.n.drop.functionality.in.editor"),
                             PropertyBinding(editorSettings::isDndEnabled, editorSettings::setDndEnabled))
private val virtualSpace
  get() = CheckboxDescriptor(message("checkbox.allow.placement.of.caret.after.end.of.line"),
                             PropertyBinding(editorSettings::isVirtualSpace, editorSettings::setVirtualSpace),
                             groupName = message("checkbox.allow.placement.of.caret.label").capitalizeWords())
private val caretInsideTabs
  get() = CheckboxDescriptor(message("checkbox.allow.placement.of.caret.inside.tabs"), PropertyBinding(editorSettings::isCaretInsideTabs,
                                                                                                       editorSettings::setCaretInsideTabs),
                             groupName = message("checkbox.allow.placement.of.caret.label").capitalizeWords())
private val virtualPageAtBottom
  get() = CheckboxDescriptor(message("checkbox.show.virtual.space.at.file.bottom"),
                             PropertyBinding(editorSettings::isAdditionalPageAtBottom, editorSettings::setAdditionalPageAtBottom))

private val highlightBraces
  get() = CheckboxDescriptor(message("checkbox.highlight.matched.brace"), codeInsightSettings::HIGHLIGHT_BRACES,
                             groupName = message("group.brace.highlighting"))
private val highlightScope
  get() = CheckboxDescriptor(message("checkbox.highlight.current.scope"), codeInsightSettings::HIGHLIGHT_SCOPE,
                             groupName = message("group.brace.highlighting"))
private val highlightIdentifierUnderCaret
  get() = CheckboxDescriptor(message("checkbox.highlight.usages.of.element.at.caret"),
                             codeInsightSettings::HIGHLIGHT_IDENTIFIER_UNDER_CARET, groupName = message("group.brace.highlighting"))

private val renameLocalVariablesInplace
  get() = CheckboxDescriptor(message("radiobutton.rename.local.variables.inplace"),
                             PropertyBinding(editorSettings::isVariableInplaceRenameEnabled,
                                             editorSettings::setVariableInplaceRenameEnabled), groupName = message(
    "radiogroup.rename.local.variables").capitalizeWords())
private val preselectCheckBox
  get() = CheckboxDescriptor(message("checkbox.rename.local.variables.preselect"), PropertyBinding(editorSettings::isPreselectRename,
                                                                                                   editorSettings::setPreselectRename),
                             groupName = message("group.refactorings"))
private val showInlineDialogForCheckBox
  get() = CheckboxDescriptor(message("checkbox.show.inline.dialog.on.local.variable.references"),
                             PropertyBinding(editorSettings::isShowInlineLocalDialog, editorSettings::setShowInlineLocalDialog))

private val cdSmoothScrolling
  get() = CheckboxDescriptor(message("checkbox.smooth.scrolling"), PropertyBinding(editorSettings::isSmoothScrolling,
                                                                                   editorSettings::setSmoothScrolling))
private val cdUseSoftWrapsAtEditor
  get() = CheckboxDescriptor(message("checkbox.use.soft.wraps.at.editor"), PropertyBinding(editorSettings::isUseSoftWraps,
                                                                                           editorSettings::setUseSoftWraps))
private val cdUseCustomSoftWrapIndent
  get() = CheckboxDescriptor(message("checkbox.use.custom.soft.wraps.indent"), PropertyBinding(editorSettings::isUseCustomSoftWrapIndent,
                                                                                               editorSettings::setUseCustomSoftWrapIndent))
private val cdShowSoftWrapsOnlyOnCaretLine
  get() = CheckboxDescriptor(message("checkbox.show.softwraps.only.for.caret.line"),
                             PropertyBinding({ !editorSettings.isAllSoftWrapsShown }, { editorSettings.setAllSoftwrapsShown(!it) }))
private val cdRemoveTrailingBlankLines
  get() = CheckboxDescriptor(message("editor.options.remove.trailing.blank.lines"),
                             PropertyBinding(editorSettings::isRemoveTrailingBlankLines, editorSettings::setRemoveTrailingBlankLines))
private val cdEnsureBlankLineBeforeCheckBox
  get() = CheckboxDescriptor(message("editor.options.line.feed"), PropertyBinding(editorSettings::isEnsureNewLineAtEOF,
                                                                                  editorSettings::setEnsureNewLineAtEOF))
private val cdShowQuickDocOnMouseMove
  get() = CheckboxDescriptor(message("editor.options.quick.doc.on.mouse.hover"),
                             PropertyBinding(editorSettings::isShowQuickDocOnMouseOverElement,
                                             editorSettings::setShowQuickDocOnMouseOverElement))
private val cdKeepTrailingSpacesOnCaretLine
  get() = CheckboxDescriptor(message("editor.settings.keep.trailing.spaces.on.caret.line"),
                             PropertyBinding(editorSettings::isKeepTrailingSpacesOnCaretLine,
                                             editorSettings::setKeepTrailingSpacesOnCaretLine))
private val cdStripTrailingSpacesEnabled
  get() = CheckboxDescriptor(message("combobox.strip.trailing.spaces.on.save"), PropertyBinding(stripTrailingSpacesProxy::isEnabled,
                                                                                                stripTrailingSpacesProxy::setEnabled))

internal val optionDescriptors: List<OptionDescription>
  get() {
    return sequenceOf(
      cbHonorCamelHumpsWhenSelectingByClicking,
      enableWheelFontChange,
      enableDnD,
      virtualSpace,
      caretInsideTabs,
      virtualPageAtBottom,
      highlightBraces,
      highlightScope,
      highlightIdentifierUnderCaret,
      renameLocalVariablesInplace,
      preselectCheckBox,
      showInlineDialogForCheckBox
    )
      .map(CheckboxDescriptor::asUiOptionDescriptor)
      .toList()
  }

private val EP_NAME = ExtensionPointName<GeneralEditorOptionsProviderEP>("com.intellij.generalEditorOptionsExtension")

class EditorOptionsPanel : BoundCompositeConfigurable<UnnamedConfigurable>(message("title.editor"), ID), WithEpDependencies {
  companion object {
    const val ID = "preferences.editor"

    private fun clearAllIdentifierHighlighters() {
      for (project in ProjectManager.getInstance().openProjects) {
        for (fileEditor in FileEditorManager.getInstance(project).allEditors) {
          if (fileEditor is TextEditor) {
            val document = fileEditor.editor.document
            IdentifierHighlighterPass.clearMyHighlights(document, project)
          }
        }
      }
    }

    @JvmStatic
    fun reinitAllEditors() {
      EditorFactory.getInstance().refreshAllEditors()
    }

    @JvmStatic
    fun restartDaemons() {
      for (project in ProjectManager.getInstance().openProjects) {
        DaemonCodeAnalyzer.getInstance(project).settingsChanged()
      }
    }
  }

  override fun createConfigurables(): List<UnnamedConfigurable> = ConfigurableWrapper.createConfigurables(EP_NAME)

  override fun getDependencies(): Collection<BaseExtensionPointName<*>> = setOf(EP_NAME)

  override fun createPanel(): DialogPanel {
    lateinit var chkEnableWheelFontSizeChange: JCheckBox
    return panel {
      group(message("group.advanced.mouse.usages")) {
        row {
          chkEnableWheelFontSizeChange = checkBox(enableWheelFontChange).component
        }
        buttonsGroup(indent = true) {
          row {
            radioButton(message("radio.enable.ctrl.mousewheel.changes.font.size.current"), false)
            radioButton(message("radio.enable.ctrl.mousewheel.changes.font.size.all"), true)
          }.enabledIf(chkEnableWheelFontSizeChange.selected)
        }.bind({ editorSettings.isWheelFontChangePersistent }, { editorSettings.isWheelFontChangePersistent = it })
        row {
          checkBox(enableDnD)
          comment(message("checkbox.enable.drag.n.drop.functionality.in.editor.comment"))
        }
      }
      group(message("group.soft.wraps")) {
        row {
          val useSoftWraps = checkBox(cdUseSoftWrapsAtEditor)
            .gap(RightGap.SMALL)
          textField()
            .bindText({ editorSettings.softWrapFileMasks }, { editorSettings.softWrapFileMasks = it })
            .columns(COLUMNS_LARGE)
            .applyToComponent { emptyText.text = message("soft.wraps.file.masks.empty.text") }
            .comment(message("soft.wraps.file.masks.hint"))
            .enabledIf(useSoftWraps.selected)
        }
        lateinit var useSoftWrapsIndent: Cell<JBCheckBox>
        row {
          useSoftWrapsIndent = checkBox(cdUseCustomSoftWrapIndent)
        }
        indent {
          row(message("label.use.custom.soft.wraps.indent")) {
            intTextField()
              .bindIntText(editorSettings::getCustomSoftWrapIndent, editorSettings::setCustomSoftWrapIndent)
              .columns(2)
              .gap(RightGap.SMALL)
            label(message("label.use.custom.soft.wraps.indent.symbols.suffix"))
          }.enabledIf(useSoftWrapsIndent.selected)
        }
        row { checkBox(cdShowSoftWrapsOnlyOnCaretLine) }
      }
      group(message("group.virtual.space")) {
        row(message("checkbox.allow.placement.of.caret.label")) {
          checkBox(virtualSpace)
          checkBox(caretInsideTabs)
        }
        row { checkBox(virtualPageAtBottom) }
      }
      group(message("group.caret.movement")) {
        caretStopRow(message("label.word.move.caret.actions.behavior"), CaretOptionMode.WORD, WordBoundary.values())
        caretStopRow(message("label.word.move.caret.actions.behavior.at.line.break"), CaretOptionMode.LINE, LineBoundary.values())
      }
      group(message("editor.options.scrolling")) {
        row { checkBox(cdSmoothScrolling) }
        buttonsGroup(message("editor.options.prefer.scrolling.editor.label")) {
          row { radioButton(message("editor.options.prefer.scrolling.editor.canvas.to.keep.caret.line.centered"), value = false) }
          row { radioButton(message("editor.options.prefer.moving.caret.line.to.minimize.editor.scrolling"), value = true) }
        }.bind(editorSettings::isRefrainFromScrolling, editorSettings::setRefrainFromScrolling)
      }
      group(message("group.richcopy")) {
        row {
          val copyShortcut = ActionManager.getInstance().getKeyboardShortcut(IdeActions.ACTION_COPY)
          val copyShortcutText = copyShortcut?.let { " (" + KeymapUtil.getShortcutText(it) + ")" } ?: ""
          checkBox(message("checkbox.enable.richcopy.label", copyShortcutText))
            .bindSelected(richCopySettings::isEnabled, richCopySettings::setEnabled)
          comment(message("checkbox.enable.richcopy.comment"))
        }
        row(message("combobox.richcopy.color.scheme")) {
          val schemes = listOf(RichCopySettings.ACTIVE_GLOBAL_SCHEME_MARKER) +
                        EditorColorsManager.getInstance().allSchemes.map { Scheme.getBaseName(it.name) }
          comboBox<String>(
            DefaultComboBoxModel(schemes.toTypedArray()),
            renderer = SimpleListCellRenderer.create("") {
              when (it) {
                RichCopySettings.ACTIVE_GLOBAL_SCHEME_MARKER ->
                  message("combobox.richcopy.color.scheme.active")
                EditorColorsScheme.DEFAULT_SCHEME_NAME -> EditorColorsScheme.DEFAULT_SCHEME_ALIAS
                else -> it
              }
            }
          ).bindItem(richCopySettings::getSchemeName, richCopySettings::setSchemeName)
        }
      }
      group(message("editor.options.save.files.group")) {
        lateinit var stripEnabledBox: Cell<JBCheckBox>
        row {
          stripEnabledBox = checkBox(cdStripTrailingSpacesEnabled)
            .gap(RightGap.SMALL)
          val model = DefaultComboBoxModel(
            arrayOf(
              EditorSettingsExternalizable.STRIP_TRAILING_SPACES_CHANGED,
              EditorSettingsExternalizable.STRIP_TRAILING_SPACES_WHOLE
            )
          )
          comboBox(
            model,
            renderer = SimpleListCellRenderer.create("") {
              when (it) {
                EditorSettingsExternalizable.STRIP_TRAILING_SPACES_CHANGED -> message("combobox.strip.modified.lines")
                EditorSettingsExternalizable.STRIP_TRAILING_SPACES_WHOLE -> message("combobox.strip.all")
                else -> it
              }
            }
          ).bindItem(stripTrailingSpacesProxy::getScope) { scope ->
            stripTrailingSpacesProxy.setScope(scope, stripEnabledBox.selected.invoke())
          }
            .enabledIf(stripEnabledBox.selected)
        }
        indent {
          row {
            checkBox(cdKeepTrailingSpacesOnCaretLine)
              .enabledIf(stripEnabledBox.selected)
          }.bottomGap(BottomGap.SMALL)
        }
        row { checkBox(cdRemoveTrailingBlankLines) }
        row { checkBox(cdEnsureBlankLineBeforeCheckBox) }
      }
      for (configurable in configurables) {
        appendDslConfigurable(configurable)
      }
    }
  }

  override fun apply() {
    val wasModified = isModified

    super.apply()

    if (wasModified) {
      clearAllIdentifierHighlighters()
      reinitAllEditors()
      UISettings.instance.fireUISettingsChanged()
      restartDaemons()
      ApplicationManager.getApplication().messageBus.syncPublisher(EditorOptionsListener.OPTIONS_PANEL_TOPIC).changesApplied()
    }
  }
}

private class EditorCodeEditingConfigurable : BoundCompositeConfigurable<ErrorOptionsProvider>(message("title.code.editing"), ID), WithEpDependencies {
  companion object {
    const val ID = "preferences.editor.code.editing"
  }

  override fun createConfigurables() = ConfigurableWrapper.createConfigurables(ErrorOptionsProviderEP.EP_NAME)
  override fun getDependencies() = setOf(ErrorOptionsProviderEP.EP_NAME)

  override fun createPanel(): DialogPanel {
    return panel {
      group(message("group.brace.highlighting")) {
        row { checkBox(highlightBraces) }
        row { checkBox(highlightScope) }
        row { checkBox(highlightIdentifierUnderCaret) }
      }
      if (!AccessibilityUtils.isScreenReaderDetected()) {
        group(message("group.quick.documentation")) {
          row { checkBox(cdShowQuickDocOnMouseMove) }
        }
      }
      if (!EditorOptionsPageCustomizer.EP_NAME.extensions().anyMatch { it.shouldHideRefactoringsSection() }) {
        group(message("group.refactorings")) {
          buttonsGroup(message("radiogroup.rename.local.variables")) {
            row { radioButton(message("radiobutton.rename.local.variables.inplace"), value = true) }
            row { radioButton(message("radiobutton.rename.local.variables.in.dialog"), value = false) }.bottomGap(BottomGap.SMALL)
          }.bind(editorSettings::isVariableInplaceRenameEnabled, editorSettings::setVariableInplaceRenameEnabled)
          row { checkBox(preselectCheckBox) }
          row { checkBox(showInlineDialogForCheckBox) }
        }
      }
      group(message("group.error.highlighting")) {
        row(message("editbox.error.stripe.mark.min.height")) {
          intTextField()
            .bindIntText(codeAnalyzerSettings::getErrorStripeMarkMinHeight, codeAnalyzerSettings::setErrorStripeMarkMinHeight)
            .columns(4)
            .gap(RightGap.SMALL)
          label(message("editbox.error.stripe.mark.min.height.pixels.suffix"))
        }.layout(RowLayout.INDEPENDENT)
        row(message("editbox.autoreparse.delay")) {
          intTextField()
            .bindIntText(codeAnalyzerSettings::getAutoReparseDelay, codeAnalyzerSettings::setAutoReparseDelay)
            .columns(4)
            .gap(RightGap.SMALL)
          label(message("editbox.autoreparse.delay.ms.suffix"))
        }.layout(RowLayout.INDEPENDENT)
        row(message("combobox.next.error.action.goes.to.label")) {
          comboBox(
            DefaultComboBoxModel(arrayOf(true, false)),
            renderer = SimpleListCellRenderer.create("") {
              when (it) {
                true -> message("combobox.next.error.action.goes.to.errors")
                false -> message("combobox.next.error.action.goes.to.all.problems")
                else -> it.toString()
              }
            }
          ).bindItem(codeAnalyzerSettings::isNextErrorActionGoesToErrorsFirst
          ) { codeAnalyzerSettings.isNextErrorActionGoesToErrorsFirst = it ?: true }
        }.layout(RowLayout.INDEPENDENT)

        for (configurable in configurables) {
          appendDslConfigurable(configurable)
        }
      }
      group(message("group.editor.tooltips")) {
        row(message("editor.options.tooltip.delay")) {
          intTextField(range = TOOLTIPS_DELAY_RANGE.asRange())
            .bindIntText(editorSettings::getTooltipsDelay, editorSettings::setTooltipsDelay)
            .columns(4)
            .gap(RightGap.SMALL)
          label(message("editor.options.ms"))
        }
      }
    }
  }
}

private fun <E : EditorCaretStopPolicyItem> Panel.caretStopRow(@Nls label: String, mode: CaretOptionMode, values: Array<E>) {
  row(label) {
    val model: DefaultComboBoxModel<E?> = SeparatorAwareComboBoxModel()
    var lastWasOsDefault = false
    for (item in values) {
      val isOsDefault = item.osDefault !== OsDefault.NONE
      if (lastWasOsDefault && !isOsDefault) model.addElement(null)
      lastWasOsDefault = isOsDefault
      val insertionIndex = if (item.osDefault.isIdeDefault) 0 else model.size
      model.insertElementAt(item, insertionIndex)
    }

    cell(ComboBox(model))
      .applyToComponent { renderer = SeparatorAwareListItemRenderer() }
      .horizontalAlign(HorizontalAlign.FILL)
      .bind(
        {
          val item = it.selectedItem as? EditorCaretStopPolicyItem
          item?.caretStopBoundary ?: mode.get(CaretStopOptionsTransposed.DEFAULT)
        },
        { it, value -> it.selectedItem = mode.find(value) },
        PropertyBinding(
          {
            val value = fromCaretStopOptions(editorSettings.caretStopOptions)
            mode.get(value)
          },
          {
            val value = fromCaretStopOptions(editorSettings.caretStopOptions)
            editorSettings.caretStopOptions = mode.update(value, it).toCaretStopOptions()
          }
        )
      )
    cell()
  }.layout(RowLayout.PARENT_GRID)
}

private class StripTrailingSpacesProxy {
  private var lastChoice: String? = getScope()

  fun isEnabled(): Boolean {
    val editorSettings = EditorSettingsExternalizable.getInstance()
    return EditorSettingsExternalizable.STRIP_TRAILING_SPACES_WHOLE == editorSettings.stripTrailingSpaces ||
           EditorSettingsExternalizable.STRIP_TRAILING_SPACES_CHANGED == editorSettings.stripTrailingSpaces
  }

  fun setEnabled(enable: Boolean) {
    if (enable != isEnabled()) {
      val editorSettings = EditorSettingsExternalizable.getInstance()
      when {
        enable -> editorSettings.stripTrailingSpaces = lastChoice
        else -> {
          lastChoice = editorSettings.stripTrailingSpaces
          editorSettings.stripTrailingSpaces = EditorSettingsExternalizable.STRIP_TRAILING_SPACES_NONE
        }
      }
    }
  }

  fun getScope(): String {
    val editorSettings = EditorSettingsExternalizable.getInstance()
    return when (editorSettings.stripTrailingSpaces) {
      EditorSettingsExternalizable.STRIP_TRAILING_SPACES_NONE -> {
        val currChoice = lastChoice
        when {
          currChoice != null -> currChoice
          else -> EditorSettingsExternalizable.STRIP_TRAILING_SPACES_CHANGED
        }
      }
      else -> editorSettings.stripTrailingSpaces
    }
  }

  fun setScope(scope: String?, enabled: Boolean) = when {
    enabled -> editorSettings.stripTrailingSpaces = scope
    else -> setEnabled(false)
  }

}

private enum class CaretOptionMode {
  WORD {
    override fun find(boundary: CaretStopBoundary): WordBoundary = WordBoundary.itemForBoundary(boundary)
    override fun get(option: CaretStopOptionsTransposed): CaretStopBoundary = option.wordBoundary
    override fun update(option: CaretStopOptionsTransposed, value: CaretStopBoundary): CaretStopOptionsTransposed =
      CaretStopOptionsTransposed(value, option.lineBoundary)
  },
  LINE {
    override fun find(boundary: CaretStopBoundary): LineBoundary = LineBoundary.itemForBoundary(boundary)
    override fun get(option: CaretStopOptionsTransposed): CaretStopBoundary = option.lineBoundary
    override fun update(option: CaretStopOptionsTransposed, value: CaretStopBoundary): CaretStopOptionsTransposed =
      CaretStopOptionsTransposed(option.wordBoundary, value)
  };

  abstract fun find(boundary: CaretStopBoundary): EditorCaretStopPolicyItem
  abstract fun get(option: CaretStopOptionsTransposed): CaretStopBoundary
  abstract fun update(option: CaretStopOptionsTransposed, value: CaretStopBoundary): CaretStopOptionsTransposed
}