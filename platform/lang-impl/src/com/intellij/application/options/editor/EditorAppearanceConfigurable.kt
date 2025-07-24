// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.editor

import com.intellij.codeInsight.actions.ReaderModeSettingsListener
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings
import com.intellij.codeInsight.documentation.render.DocRenderManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.extensions.BaseExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.BoundCompositeSearchableConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.options.ex.ConfigurableWrapper
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.util.PlatformUtils
import javax.swing.DefaultComboBoxModel

// @formatter:off
private val model:EditorSettingsExternalizable
  get() = EditorSettingsExternalizable.getInstance()

private val myCbBlinkCaret                            get() = CheckboxDescriptor(ApplicationBundle.message("checkbox.caret.blinking.ms"), model::isBlinkCaret, model::setBlinkCaret)
private val myCbBlockCursor                           get() = CheckboxDescriptor(ApplicationBundle.message("checkbox.use.block.caret"), model::isBlockCursor, model::setBlockCursor)
private val myCbFullLineHeightCursor                  get() = CheckboxDescriptor(ApplicationBundle.message("checkbox.use.full.line.height.caret"), model::isFullLineHeightCursor, model::setFullLineHeightCursor)
private val myCbHighlightSelectionOccurrences         get() = CheckboxDescriptor(ApplicationBundle.message("checkbox.highlight.selection.occurrences"), model::isHighlightSelectionOccurrences, model::setHighlightSelectionOccurrences)
private val myCbRightMargin                           get() = CheckboxDescriptor(ApplicationBundle.message("checkbox.right.margin"), model::isRightMarginShown, model::setRightMarginShown)
private val myCbShowLineNumbers                       get() = CheckboxDescriptor(ApplicationBundle.message("checkbox.show.line.numbers"), model::isLineNumbersShown, model::setLineNumbersShown)
private val myCbShowMethodSeparators                  get() = CheckboxDescriptor(if (PlatformUtils.isDataGrip()) ApplicationBundle.message("checkbox.show.method.separators.DataGrip") else  ApplicationBundle.message("checkbox.show.method.separators"), DaemonCodeAnalyzerSettings.getInstance()::SHOW_METHOD_SEPARATORS)
private val myWhitespacesCheckbox                     get() = CheckboxDescriptor(ApplicationBundle.message("checkbox.show.whitespaces"), model::isWhitespacesShown, model::setWhitespacesShown)
private val myLeadingWhitespacesCheckBox              get() = CheckboxDescriptor(ApplicationBundle.message("checkbox.show.leading.whitespaces"), model::isLeadingWhitespacesShown, model::setLeadingWhitespacesShown)
private val myInnerWhitespacesCheckBox                get() = CheckboxDescriptor(ApplicationBundle.message("checkbox.show.inner.whitespaces"), model::isInnerWhitespacesShown, model::setInnerWhitespacesShown)
private val myTrailingWhitespacesCheckBox             get() = CheckboxDescriptor(ApplicationBundle.message("checkbox.show.trailing.whitespaces"), model::isTrailingWhitespacesShown, model::setTrailingWhitespacesShown)
private val mySelectionWhitespacesCheckBox            get() = CheckboxDescriptor(ApplicationBundle.message("checkbox.show.selection.whitespaces"), model::isSelectionWhitespacesShown, model::setSelectionWhitespacesShown)
private val myShowVerticalIndentGuidesCheckBox        get() = CheckboxDescriptor(ApplicationBundle.message("checkbox.show.indent.guides"), model::isIndentGuidesShown, model::setIndentGuidesShown)
private val myFocusModeCheckBox                       get() = CheckboxDescriptor(ApplicationBundle.message("checkbox.highlight.only.current.declaration"), model::isFocusMode, model::setFocusMode)
private val myCbShowIntentionBulbCheckBox             get() = CheckboxDescriptor(ApplicationBundle.message("checkbox.show.intention.bulb"), model::isShowIntentionBulb, model::setShowIntentionBulb)
private val myShowIntentionPreviewCheckBox            get() = CheckboxDescriptor(ApplicationBundle.message("checkbox.show.intention.preview"), model::isShowIntentionPreview, model::setShowIntentionPreview)
private val myCodeLensCheckBox                        get() = CheckboxDescriptor(IdeBundle.message("checkbox.show.editor.preview.popup"), UISettings.getInstance()::showEditorToolTip)
private val myRenderedDocCheckBox                     get() = CheckboxDescriptor(IdeBundle.message("checkbox.show.rendered.doc.comments"), model::isDocCommentRenderingEnabled, model::setDocCommentRenderingEnabled)
private val myUseEditorFontInInlays                   get() = CheckboxDescriptor(ApplicationBundle.message("use.editor.font.for.inlays"), model::isUseEditorFontInInlays, model::setUseEditorFontInInlays)
// @formatter:on

internal class EditorAppearanceConfigurable : BoundCompositeSearchableConfigurable<UnnamedConfigurable>(
  ApplicationBundle.message("tab.editor.settings.appearance"),
  "reference.settingsdialog.IDE.editor.appearance",
  "editor.preferences.appearance"
), Configurable.WithEpDependencies {
  override fun createPanel(): DialogPanel {
    val model = EditorSettingsExternalizable.getInstance()
    return panel {
      row {
        val cbBlinkCaret = checkBox(myCbBlinkCaret)
          .gap(RightGap.SMALL)
        intTextField(range = EditorSettingsExternalizable.BLINKING_RANGE.asRange(), keyboardStep = 100)
          .bindIntText(model::getBlinkPeriod, model::setBlinkPeriod)
          .columns(5)
          .enabledIf(cbBlinkCaret.selected)
      }
      row {
        checkBox(myCbBlockCursor)
      }
      row {
        checkBox(myCbFullLineHeightCursor)
      }
      row {
        checkBox(myCbHighlightSelectionOccurrences)
      }
      row {
        checkBox(myCbRightMargin)
      }
      row {
        checkBox(myCbShowLineNumbers)
        comboBox(
          DefaultComboBoxModel(EditorSettings.LineNumerationType.values()),
          renderer = textListCellRenderer {
            when (it) {
              EditorSettings.LineNumerationType.ABSOLUTE -> ApplicationBundle.message("line.numeration.type.absolute")
              EditorSettings.LineNumerationType.RELATIVE -> ApplicationBundle.message("line.numeration.type.relative")
              EditorSettings.LineNumerationType.HYBRID -> ApplicationBundle.message("line.numeration.type.hybrid")
              null -> ""
            }
          }
        ).bindItem(model::getLineNumeration, model::setLineNumeration)
      }
      row {
        checkBox(myCbShowMethodSeparators)
      }

      lateinit var cbWhitespace: Cell<JBCheckBox>
      row {
        cbWhitespace = checkBox(myWhitespacesCheckbox)
      }

      indent {
        row {
          checkBox(myLeadingWhitespacesCheckBox)
        }
        row {
          checkBox(myInnerWhitespacesCheckBox)
        }
        row {
          checkBox(myTrailingWhitespacesCheckBox)
        }
        row {
          checkBox(mySelectionWhitespacesCheckBox)
        }
      }.enabledIf(cbWhitespace.selected)

      row {
        checkBox(myShowVerticalIndentGuidesCheckBox)
      }
      if (ApplicationManager.getApplication().isInternal) {
        row {
          checkBox(myFocusModeCheckBox)
        }
      }
      row {
        checkBox(myCbShowIntentionBulbCheckBox)
      }
      row {
        checkBox(myShowIntentionPreviewCheckBox)
      }
      row {
        checkBox(myRenderedDocCheckBox)
          .commentRight(IdeBundle.message("checkbox.also.in.reader.mode")) {
            ReaderModeSettingsListener.goToEditorReaderMode()
          }
      }
      row {
        checkBox(myCodeLensCheckBox)
      }
      row {
        checkBox(myUseEditorFontInInlays)
      }

      for (configurable in configurables) {
        appendDslConfigurable(configurable)
      }
    }
  }

  override fun createConfigurables(): List<UnnamedConfigurable> {
    return ConfigurableWrapper.createConfigurables(EP_NAME)
  }

  override fun getDependencies(): Collection<BaseExtensionPointName<*>> {
    return listOf(EP_NAME)
  }

  override fun apply() {
    val showEditorTooltip = UISettings.getInstance().showEditorToolTip
    val docRenderingEnabled = EditorSettingsExternalizable.getInstance().isDocCommentRenderingEnabled

    super.apply()

    reinitAllEditors()
    if (showEditorTooltip != UISettings.getInstance().showEditorToolTip) {
      LafManager.getInstance().repaintUI()
      UISettings.getInstance().fireUISettingsChanged()
    }
    if (docRenderingEnabled != EditorSettingsExternalizable.getInstance().isDocCommentRenderingEnabled) {
      DocRenderManager.resetAllEditorsToDefaultState()
    }

    restartDaemons()
    ApplicationManager.getApplication().messageBus.syncPublisher(EditorOptionsListener.APPEARANCE_CONFIGURABLE_TOPIC).changesApplied()
  }

  private val EP_NAME = ExtensionPointName.create<EditorAppearanceConfigurableEP>("com.intellij.editorAppearanceConfigurable")

}
