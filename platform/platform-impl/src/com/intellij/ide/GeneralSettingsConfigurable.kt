// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.application.options.editor.CheckboxDescriptor
import com.intellij.application.options.editor.checkBox
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.PathChooserDialog
import com.intellij.openapi.help.HelpManager
import com.intellij.openapi.options.BoundCompositeSearchableConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ex.ConfigurableWrapper
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.IdeUICustomization
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.*
import com.intellij.util.PlatformUtils

// @formatter:off
private val model = GeneralSettings.getInstance()
private val myChkReopenLastProject                get() = CheckboxDescriptor(IdeUICustomization.getInstance().projectMessage("checkbox.reopen.last.project.on.startup"), PropertyBinding(model::isReopenLastProject, model::setReopenLastProject))
private val myConfirmExit                         get() = CheckboxDescriptor(IdeBundle.message("checkbox.confirm.application.exit"), PropertyBinding(model::isConfirmExit, model::setConfirmExit))
private val mySkipWelcomeScreen                   get() = CheckboxDescriptor(IdeBundle.message("checkbox.skip.welcome.screen"), PropertyBinding({ !model.isShowWelcomeScreen }, { model.isShowWelcomeScreen = !it }))
private val myChkSyncOnFrameActivation            get() = CheckboxDescriptor(IdeBundle.message("checkbox.synchronize.files.on.frame.activation"), PropertyBinding(model::isSyncOnFrameActivation, model::setSyncOnFrameActivation))
private val myChkSaveOnFrameDeactivation          get() = CheckboxDescriptor(IdeBundle.message("checkbox.save.files.on.frame.deactivation"), PropertyBinding(model::isSaveOnFrameDeactivation, model::setSaveOnFrameDeactivation))
private val myChkAutoSaveIfInactive               get() = CheckboxDescriptor(IdeBundle.message("checkbox.save.files.automatically"), PropertyBinding(model::isAutoSaveIfInactive, model::setAutoSaveIfInactive))
private val myChkUseSafeWrite                     get() = CheckboxDescriptor(IdeBundle.message("checkbox.safe.write"), PropertyBinding(model::isUseSafeWrite, model::setUseSafeWrite))
// @formatter:on

internal val allOptionDescriptors
  get() = sequenceOf(
    myChkReopenLastProject,
    myConfirmExit,
    myChkSyncOnFrameActivation,
    myChkSaveOnFrameDeactivation,
    myChkAutoSaveIfInactive,
    myChkUseSafeWrite
  )
    .map { it.asUiOptionDescriptor() }
    .toList()

/**
 * To provide additional options in General section register implementation of {@link SearchableConfigurable} in the plugin.xml:
 * <p/>
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;<br>
 * &nbsp;&nbsp;&lt;generalOptionsProvider instance="class-name"/&gt;<br>
 * &lt;/extensions&gt;
 * <p>
 * A new instance of the specified class will be created each time then the Settings dialog is opened
 */
class GeneralSettingsConfigurable: BoundCompositeSearchableConfigurable<SearchableConfigurable>(
  IdeBundle.message("title.general"),
  "preferences.general"
), SearchableConfigurable {
  private val model = GeneralSettings.getInstance()

  override fun createPanel(): DialogPanel {
    return panel {
      row {
        checkBox(myConfirmExit)
      }

      buttonsGroup {
        row(IdeBundle.message("group.settings.process.tab.close")) {
          radioButton(IdeBundle.message("radio.process.close.terminate"), GeneralSettings.ProcessCloseConfirmation.TERMINATE)
          radioButton(IdeBundle.message("radio.process.close.disconnect"), GeneralSettings.ProcessCloseConfirmation.DISCONNECT)
          radioButton(IdeBundle.message("radio.process.close.ask"), GeneralSettings.ProcessCloseConfirmation.ASK)
        }
      }.bind(model::getProcessCloseConfirmation, model::setProcessCloseConfirmation)

      group(IdeUICustomization.getInstance().projectMessage("tab.title.project")) {
        row {
          checkBox(myChkReopenLastProject)
        }
        buttonsGroup {
          row(IdeUICustomization.getInstance().projectMessage("label.open.project.in")) {
            radioButton(IdeUICustomization.getInstance().projectMessage("radio.button.open.project.in.the.new.window"),
                        GeneralSettings.OPEN_PROJECT_NEW_WINDOW)
            radioButton(IdeUICustomization.getInstance().projectMessage("radio.button.open.project.in.the.same.window"),
                        GeneralSettings.OPEN_PROJECT_SAME_WINDOW)
            radioButton(IdeUICustomization.getInstance().projectMessage("radio.button.confirm.window.to.open.project.in"),
                        GeneralSettings.OPEN_PROJECT_ASK)
            if (PlatformUtils.isDataSpell()) {
              radioButton(IdeUICustomization.getInstance().projectMessage("radio.button.attach"),
                          GeneralSettings.OPEN_PROJECT_SAME_WINDOW_ATTACH)
            }
          }.layout(RowLayout.INDEPENDENT)
        }.bind(model::getConfirmOpenNewProject, model::setConfirmOpenNewProject)

        if (PlatformUtils.isDataSpell()) {
          row {
            checkBox(mySkipWelcomeScreen)
          }
        }
        row(IdeUICustomization.getInstance().projectMessage("settings.general.default.directory")) {
          textFieldWithBrowseButton(fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
                                      .also { it.putUserData(PathChooserDialog.PREFER_LAST_OVER_EXPLICIT, false) })
            .bindText(model::getDefaultProjectDirectory, model::setDefaultProjectDirectory)
            .columns(COLUMNS_MEDIUM)
            .comment(IdeBundle.message("settings.general.directory.preselected"), 80)
        }
      }

      group(IdeBundle.message("settings.general.synchronization")) {
        row {
          val autoSaveCheckbox = checkBox(myChkAutoSaveIfInactive).gap(RightGap.SMALL)
          intTextField(GeneralSettings.SAVE_FILES_AFTER_IDLE_SEC.asRange())
            .bindIntText(model::getInactiveTimeout, model::setInactiveTimeout)
            .columns(4)
            .enabledIf(autoSaveCheckbox.selected)
            .gap(RightGap.SMALL)
          @Suppress("DialogTitleCapitalization")
          label(IdeBundle.message("label.inactive.timeout.sec"))
        }
        row {
          checkBox(myChkSaveOnFrameDeactivation)
        }
        row {
          checkBox(myChkUseSafeWrite)
        }
        row {
          checkBox(myChkSyncOnFrameActivation)
        }
        row {
          comment(IdeBundle.message("label.autosave.comment")) {
            HelpManager.getInstance().invokeHelp("autosave")
          }
        }.topGap(TopGap.SMALL)
      }

      for (configurable in configurables) {
        appendDslConfigurable(configurable)
      }
    }
  }

  override fun getId(): String = helpTopic!!

  override fun createConfigurables(): List<SearchableConfigurable> {
    return ConfigurableWrapper.createConfigurables(EP_NAME)
  }

  companion object {
    private val EP_NAME = ExtensionPointName.create<GeneralSettingsConfigurableEP>("com.intellij.generalOptionsProvider")
  }
}
