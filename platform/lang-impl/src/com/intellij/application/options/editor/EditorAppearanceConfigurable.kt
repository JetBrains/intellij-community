// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.editor

import com.intellij.codeInsight.actions.ReaderModeSettingsListener
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings
import com.intellij.codeInsight.documentation.render.DocRenderManager
import com.intellij.formatting.visualLayer.VisualFormattingLayerService
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.ApplicationManager
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
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.*
import com.intellij.util.PlatformUtils

// @formatter:off
private val model = EditorSettingsExternalizable.getInstance()
private val daemonCodeAnalyzerSettings = DaemonCodeAnalyzerSettings.getInstance()
private val uiSettings = UISettings.instance

private val myCbBlinkCaret                            get() = CheckboxDescriptor(ApplicationBundle.message("checkbox.caret.blinking.ms"), PropertyBinding(model::isBlinkCaret, model::setBlinkCaret))
private val myCbBlockCursor                           get() = CheckboxDescriptor(ApplicationBundle.message("checkbox.use.block.caret"), PropertyBinding(model::isBlockCursor, model::setBlockCursor))
private val myCbRightMargin                           get() = CheckboxDescriptor(ApplicationBundle.message("checkbox.right.margin"), PropertyBinding(model::isRightMarginShown, model::setRightMarginShown))
private val myCbShowLineNumbers                       get() = CheckboxDescriptor(ApplicationBundle.message("checkbox.show.line.numbers"), PropertyBinding(model::isLineNumbersShown, model::setLineNumbersShown))
private val myCbShowMethodSeparators                  get() = CheckboxDescriptor(if (PlatformUtils.isDataGrip()) ApplicationBundle.message("checkbox.show.method.separators.DataGrip") else  ApplicationBundle.message("checkbox.show.method.separators"), daemonCodeAnalyzerSettings::SHOW_METHOD_SEPARATORS.toBinding())
private val myWhitespacesCheckbox                     get() = CheckboxDescriptor(ApplicationBundle.message("checkbox.show.whitespaces"), PropertyBinding(model::isWhitespacesShown, model::setWhitespacesShown))
private val myLeadingWhitespacesCheckBox              get() = CheckboxDescriptor(ApplicationBundle.message("checkbox.show.leading.whitespaces"), PropertyBinding(model::isLeadingWhitespacesShown, model::setLeadingWhitespacesShown))
private val myInnerWhitespacesCheckBox                get() = CheckboxDescriptor(ApplicationBundle.message("checkbox.show.inner.whitespaces"), PropertyBinding(model::isInnerWhitespacesShown, model::setInnerWhitespacesShown))
private val myTrailingWhitespacesCheckBox             get() = CheckboxDescriptor(ApplicationBundle.message("checkbox.show.trailing.whitespaces"), PropertyBinding(model::isTrailingWhitespacesShown, model::setTrailingWhitespacesShown))
private val myShowVerticalIndentGuidesCheckBox        get() = CheckboxDescriptor(ApplicationBundle.message("checkbox.show.indent.guides"), PropertyBinding(model::isIndentGuidesShown, model::setIndentGuidesShown))
private val myFocusModeCheckBox                       get() = CheckboxDescriptor(ApplicationBundle.message("checkbox.highlight.only.current.declaration"), PropertyBinding(model::isFocusMode, model::setFocusMode))
private val myCbShowIntentionBulbCheckBox             get() = CheckboxDescriptor(ApplicationBundle.message("checkbox.show.intention.bulb"), PropertyBinding(model::isShowIntentionBulb, model::setShowIntentionBulb))
private val myCodeLensCheckBox                        get() = CheckboxDescriptor(IdeBundle.message("checkbox.show.editor.preview.popup"), uiSettings::showEditorToolTip)
private val myRenderedDocCheckBox                     get() = CheckboxDescriptor(IdeBundle.message("checkbox.show.rendered.doc.comments"), PropertyBinding(model::isDocCommentRenderingEnabled, model::setDocCommentRenderingEnabled))
private val myUseEditorFontInInlays                   get() = CheckboxDescriptor(ApplicationBundle.message("use.editor.font.for.inlays"), PropertyBinding(model::isUseEditorFontInInlays, model::setUseEditorFontInInlays))
private val myCdShowVisualFormattingLayer             get() = CheckboxDescriptor(IdeBundle.message("checkbox.show.visual.formatting.layer"), uiSettings::showVisualFormattingLayer)
// @formatter:on

class EditorAppearanceConfigurable : BoundCompositeSearchableConfigurable<UnnamedConfigurable>(
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
        checkBox(myCbRightMargin)
      }
      row {
        checkBox(myCbShowLineNumbers)
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
        checkBox(myRenderedDocCheckBox)
        comment(IdeBundle.message("checkbox.also.in.reader.mode")) {
          ReaderModeSettingsListener.goToEditorReaderMode()
        }
      }
      row {
        checkBox(myCodeLensCheckBox)
      }
      row {
        checkBox(myUseEditorFontInInlays)
      }

      VisualFormattingLayerService.getInstance()
        .takeIf { it.enabledByRegistry }
        ?.let { service ->
          lateinit var checkbox: Cell<JBCheckBox>
          val apply = {
            ApplicationManager.getApplication().invokeLater(service::refreshGlobally)
          }
          row {
            checkbox = checkBox(myCdShowVisualFormattingLayer)
              .onApply(apply)
          }
          indent {
            row(IdeBundle.message("combobox.label.visual.formatting.layer.scheme")) {
              comboBox(service.getSchemes())
                .bindItem(service::scheme)
                .onApply(apply)
            }
          }
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
    val showEditorTooltip = UISettings.instance.showEditorToolTip
    val docRenderingEnabled = EditorSettingsExternalizable.getInstance().isDocCommentRenderingEnabled

    super.apply()

    EditorOptionsPanel.reinitAllEditors()
    if (showEditorTooltip != UISettings.instance.showEditorToolTip) {
      LafManager.getInstance().repaintUI()
      uiSettings.fireUISettingsChanged()
    }
    if (docRenderingEnabled != EditorSettingsExternalizable.getInstance().isDocCommentRenderingEnabled) {
      DocRenderManager.resetAllEditorsToDefaultState()
    }

    EditorOptionsPanel.restartDaemons()
    ApplicationManager.getApplication().messageBus.syncPublisher(EditorOptionsListener.APPEARANCE_CONFIGURABLE_TOPIC).changesApplied()
  }

  companion object {
    private val EP_NAME = ExtensionPointName.create<EditorAppearanceConfigurableEP>("com.intellij.editorAppearanceConfigurable")
  }
}
