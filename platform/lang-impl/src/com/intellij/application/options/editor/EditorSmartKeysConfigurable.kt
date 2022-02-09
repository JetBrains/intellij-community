// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.editor

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.editorActions.SmartBackspaceMode
import com.intellij.ide.ui.search.BooleanOptionDescription
import com.intellij.lang.CodeDocumentationAwareCommenter
import com.intellij.lang.Language
import com.intellij.lang.LanguageCommenters
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.keymap.impl.ModifierKeyDoubleClickHandler
import com.intellij.openapi.options.BoundCompositeSearchableConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.options.ex.ConfigurableWrapper
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.EnumComboBoxModel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.*
import org.jetbrains.annotations.NonNls
import java.awt.event.KeyEvent
import javax.swing.DefaultComboBoxModel

private val editorSettings: EditorSettingsExternalizable
  get() = EditorSettingsExternalizable.getInstance()
private val codeInsightSettings: CodeInsightSettings
  get() = CodeInsightSettings.getInstance()

private val cbSmartHome
  get() = CheckboxDescriptor(ApplicationBundle.message("checkbox.smart.home"),
                             PropertyBinding(editorSettings::isSmartHome, editorSettings::setSmartHome))
private val cbSmartEnd
  get() = CheckboxDescriptor(ApplicationBundle.message("checkbox.smart.end.on.blank.line"),
                             codeInsightSettings::SMART_END_ACTION.toBinding())
private val cbInsertPairBracket
  get() = CheckboxDescriptor(ApplicationBundle.message("checkbox.insert.pair.bracket"),
                             codeInsightSettings::AUTOINSERT_PAIR_BRACKET.toBinding())
private val cbInsertPairQuote
  get() = CheckboxDescriptor(ApplicationBundle.message("checkbox.insert.pair.quote"),
                             codeInsightSettings::AUTOINSERT_PAIR_QUOTE.toBinding())
private val cbReformatBlockOnTypingRBrace
  get() = CheckboxDescriptor(ApplicationBundle.message("checkbox.reformat.on.typing.rbrace"),
                             codeInsightSettings::REFORMAT_BLOCK_ON_RBRACE.toBinding())
private val cbCamelWords
  get() = CheckboxDescriptor(ApplicationBundle.message("checkbox.use.camelhumps.words"),
                             PropertyBinding(editorSettings::isCamelWords, editorSettings::setCamelWords))
private val cbSurroundSelectionOnTyping
  get() = CheckboxDescriptor(ApplicationBundle.message("checkbox.surround.selection.on.typing.quote.or.brace"),
                             codeInsightSettings::SURROUND_SELECTION_ON_QUOTE_TYPED.toBinding())
private val cbTabExistsBracketsAndQuotes
  get() = CheckboxDescriptor(ApplicationBundle.message("checkbox.tab.exists.brackets.and.quotes"),
                             codeInsightSettings::TAB_EXITS_BRACKETS_AND_QUOTES.toBinding())
private val cbEnableAddingCaretsOnDoubleCtrlArrows
  get() = CheckboxDescriptor(ApplicationBundle.message("checkbox.enable.double.ctrl",
                                                       KeyEvent.getKeyText(ModifierKeyDoubleClickHandler.getMultiCaretActionModifier())),
                             PropertyBinding(editorSettings::addCaretsOnDoubleCtrl, editorSettings::setAddCaretsOnDoubleCtrl))
private val cbSmartIndentOnEnter
  get() = CheckboxDescriptor(ApplicationBundle.message("checkbox.smart.indent"), codeInsightSettings::SMART_INDENT_ON_ENTER.toBinding())
private val cbInsertPairCurlyBraceOnEnter
  get() = CheckboxDescriptor(ApplicationBundle.message("checkbox.insert.pair.curly.brace"),
                             codeInsightSettings::INSERT_BRACE_ON_ENTER.toBinding())
private val cbInsertJavadocStubOnEnter
  get() = CheckboxDescriptor(ApplicationBundle.message("checkbox.javadoc.stub.after.slash.star.star"),
                             codeInsightSettings::JAVADOC_STUB_ON_ENTER.toBinding())
internal val cbHonorCamelHumpsWhenSelectingByClicking
  get() = CheckboxDescriptor(ApplicationBundle.message("checkbox.honor.camelhumps.words.settings.on.double.click"),
                             PropertyBinding(editorSettings::isMouseClickSelectionHonorsCamelWords,
                                             editorSettings::setMouseClickSelectionHonorsCamelWords))

internal val editorSmartKeysOptionDescriptors: List<BooleanOptionDescription>
  get() = listOf(
    cbSmartHome,
    cbSmartEnd,
    cbInsertPairBracket,
    cbInsertPairQuote,
    cbReformatBlockOnTypingRBrace,
    cbCamelWords,
    cbSurroundSelectionOnTyping,
    cbTabExistsBracketsAndQuotes,
    cbEnableAddingCaretsOnDoubleCtrlArrows,
    cbSmartIndentOnEnter,
    cbInsertPairCurlyBraceOnEnter,
    cbInsertJavadocStubOnEnter,
    cbHonorCamelHumpsWhenSelectingByClicking
  ).map(CheckboxDescriptor::asUiOptionDescriptor)

@NonNls
internal const val ID = "editor.preferences.smartKeys"

private val EP_NAME = ExtensionPointName<EditorSmartKeysConfigurableEP>("com.intellij.editorSmartKeysConfigurable")

/**
 * To provide additional options in Editor | Smart Keys section register implementation of {@link com.intellij.openapi.options.UnnamedConfigurable} in the plugin.xml:
 * <p/>
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;<br>
 * &nbsp;&nbsp;&lt;editorSmartKeysConfigurable instance="class-name"/&gt;<br>
 * &lt;/extensions&gt;
 * <p>
 * A new instance of the specified class will be created each time then the Settings dialog is opened
 */
class EditorSmartKeysConfigurable : Configurable.WithEpDependencies, BoundCompositeSearchableConfigurable<UnnamedConfigurable>(
  ApplicationBundle.message("group.smart.keys"),
  "reference.settingsdialog.IDE.editor.smartkey",
  ID
), SearchableConfigurable.Parent {
  override fun createPanel(): DialogPanel {
    return panel {
      row {
        checkBox(cbSmartHome)
      }
      row {
        checkBox(cbSmartEnd)
      }
      row {
        checkBox(cbInsertPairBracket)
      }
      row {
        checkBox(cbInsertPairQuote)
      }
      row {
        checkBox(cbReformatBlockOnTypingRBrace)
      }
      row {
        checkBox(cbCamelWords)
      }
      row {
        checkBox(cbHonorCamelHumpsWhenSelectingByClicking)
      }
      row {
        checkBox(cbSurroundSelectionOnTyping)
      }
      row {
        checkBox(cbEnableAddingCaretsOnDoubleCtrlArrows)
      }
      row {
        checkBox(cbTabExistsBracketsAndQuotes)
      }
      group(ApplicationBundle.message("group.enter.title")) {
        row {
          checkBox(cbSmartIndentOnEnter)
        }
        row {
          checkBox(cbInsertPairCurlyBraceOnEnter)
        }
        if (hasAnyDocAwareCommenters()) {
          row {
            checkBox(cbInsertJavadocStubOnEnter)
          }
        }
      }
      row(ApplicationBundle.message("combobox.smart.backspace")) {
        comboBox(
          EnumComboBoxModel(SmartBackspaceMode::class.java),
          renderer = listCellRenderer { value, _, _ ->
            setText(when(value) {
              SmartBackspaceMode.OFF -> ApplicationBundle.message("combobox.smart.backspace.off")
              SmartBackspaceMode.INDENT -> ApplicationBundle.message("combobox.smart.backspace.simple")
              SmartBackspaceMode.AUTOINDENT -> ApplicationBundle.message("combobox.smart.backspace.smart")
              else -> ""
            })
          })
          .bindItem(PropertyBinding(codeInsightSettings::getBackspaceMode, codeInsightSettings::setBackspaceMode).toNullable())
      }
      row(ApplicationBundle.message("combobox.paste.reformat")) {
        comboBox(
          DefaultComboBoxModel(arrayOf(CodeInsightSettings.NO_REFORMAT, CodeInsightSettings.INDENT_BLOCK, CodeInsightSettings.INDENT_EACH_LINE, CodeInsightSettings.REFORMAT_BLOCK)),
          renderer = listCellRenderer { value, _, _ ->
            setText(when(value) {
                      CodeInsightSettings.NO_REFORMAT -> ApplicationBundle.message("combobox.paste.reformat.none")
                      CodeInsightSettings.INDENT_BLOCK -> ApplicationBundle.message("combobox.paste.reformat.indent.block")
                      CodeInsightSettings.INDENT_EACH_LINE -> ApplicationBundle.message("combobox.paste.reformat.indent.each.line")
                      CodeInsightSettings.REFORMAT_BLOCK -> ApplicationBundle.message("combobox.paste.reformat.reformat.block")
              else -> ""
            })
          }
        ).bindItem(codeInsightSettings::REFORMAT_ON_PASTE)
      }
      for (configurable in configurables) {
        appendDslConfigurable(configurable)
      }
    }
  }

  private fun hasAnyDocAwareCommenters(): Boolean {
    return Language.getRegisteredLanguages().any {
      val commenter = LanguageCommenters.INSTANCE.forLanguage(it)
      commenter is CodeDocumentationAwareCommenter && commenter.documentationCommentLinePrefix != null
    }
  }

  override fun apply() {
    super.apply()
    ApplicationManager.getApplication().messageBus.syncPublisher(EditorOptionsListener.SMART_KEYS_CONFIGURABLE_TOPIC).changesApplied()
  }

  private val allConfigurables: List<UnnamedConfigurable> by lazy { ConfigurableWrapper.createConfigurables(EP_NAME) }

  override fun createConfigurables(): List<UnnamedConfigurable> {
    return allConfigurables.filterNot { it is Configurable }
  }

  override fun getConfigurables(): Array<Configurable> {
    return allConfigurables.filterIsInstance<Configurable>().toTypedArray()
  }

  override fun hasOwnContent() = true

  override fun getDependencies() = listOf(EP_NAME)
}
