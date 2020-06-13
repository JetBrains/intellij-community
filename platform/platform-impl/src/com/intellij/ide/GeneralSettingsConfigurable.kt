// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.application.options.editor.CheckboxDescriptor
import com.intellij.application.options.editor.checkBox
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.PathChooserDialog
import com.intellij.openapi.options.BoundCompositeConfigurable
import com.intellij.openapi.options.BoundCompositeSearchableConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ex.ConfigurableWrapper
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.IdeUICustomization
import com.intellij.ui.layout.*
import com.intellij.util.PlatformUtils

// @formatter:off
private val model = GeneralSettings.getInstance()
private val myChkReopenLastProject                get() = CheckboxDescriptor(IdeUICustomization.getInstance().projectMessage("checkbox.reopen.last.project.on.startup"), PropertyBinding(model::isReopenLastProject, model::setReopenLastProject))
private val myConfirmExit                         get() = CheckboxDescriptor(IdeBundle.message("checkbox.confirm.application.exit"), PropertyBinding(model::isConfirmExit, model::setConfirmExit))
private val myShowWelcomeScreen                   get() = CheckboxDescriptor(IdeBundle.message("checkbox.show.welcome.screen"), PropertyBinding(model::isShowWelcomeScreen, model::setShowWelcomeScreen))
private val myChkSyncOnFrameActivation            get() = CheckboxDescriptor(IdeBundle.message("checkbox.synchronize.files.on.frame.activation"), PropertyBinding(model::isSyncOnFrameActivation, model::setSyncOnFrameActivation))
private val myChkSaveOnFrameDeactivation          get() = CheckboxDescriptor(IdeBundle.message("checkbox.save.files.on.frame.deactivation"), PropertyBinding(model::isSaveOnFrameDeactivation, model::setSaveOnFrameDeactivation))
private val myChkAutoSaveIfInactive               get() = CheckboxDescriptor(IdeBundle.message("checkbox.save.files.automatically"), PropertyBinding(model::isAutoSaveIfInactive, model::setAutoSaveIfInactive))
private val myChkUseSafeWrite                     get() = CheckboxDescriptor(IdeBundle.message("checkbox.safe.write"), PropertyBinding(model::isUseSafeWrite, model::setUseSafeWrite))
// @formatter:on

internal val allOptionDescriptors
  get() = listOf(
    myChkReopenLastProject,
    myConfirmExit,
    myChkSyncOnFrameActivation,
    myChkSaveOnFrameDeactivation,
    myChkAutoSaveIfInactive,
    myChkUseSafeWrite
  ).map { it.asUiOptionDescriptor() }

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
      row {
        cell {
          label(IdeBundle.message("group.settings.process.tab.close"))
          buttonGroup(model::getProcessCloseConfirmation, model::setProcessCloseConfirmation) {
            radioButton(IdeBundle.message("radio.process.close.terminate"), GeneralSettings.ProcessCloseConfirmation.TERMINATE)
            radioButton(IdeBundle.message("radio.process.close.disaconnect"), GeneralSettings.ProcessCloseConfirmation.DISCONNECT)
            radioButton(IdeBundle.message("radio.process.close.ask"), GeneralSettings.ProcessCloseConfirmation.ASK)
          }
        }
      }
      titledRow(IdeUICustomization.getInstance().projectMessage("tab.title.project")) {
        row {
          checkBox(myChkReopenLastProject)
        }
        row {
          cell(isFullWidth = true) {
            label(IdeUICustomization.getInstance().projectMessage("label.open.project.in"))
            buttonGroup(model::getConfirmOpenNewProject, model::setConfirmOpenNewProject) {
              radioButton(IdeUICustomization.getInstance().projectMessage("radio.button.open.project.in.the.new.window"), GeneralSettings.OPEN_PROJECT_NEW_WINDOW)
              radioButton(IdeUICustomization.getInstance().projectMessage("radio.button.open.project.in.the.same.window"), GeneralSettings.OPEN_PROJECT_SAME_WINDOW)
              radioButton(IdeUICustomization.getInstance().projectMessage("radio.button.confirm.window.to.open.project.in"), GeneralSettings.OPEN_PROJECT_ASK)
            }
          }
        }

        if (PlatformUtils.isDataGrip()) {
          row {
            checkBox(myShowWelcomeScreen)
          }
        }
        row {
          cell {
            label(IdeUICustomization.getInstance().projectMessage("settings.general.default.directory"))
            textFieldWithBrowseButton(model::getDefaultProjectDirectory, model::setDefaultProjectDirectory,
                                      fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
                                        .also { it.putUserData(PathChooserDialog.PREFER_LAST_OVER_EXPLICIT, false) })
              .growPolicy(GrowPolicy.MEDIUM_TEXT)
              .comment(IdeBundle.message("settings.general.directory.preselected"), 80)
          }
        }
      }
      titledRow(IdeBundle.message("settings.general.synchronization")) {
        row {
          cell(isFullWidth = true) {
            val autoSaveCheckbox = checkBox(myChkAutoSaveIfInactive)
            intTextField(model::getInactiveTimeout, model::setInactiveTimeout, columns = 4).enableIf(autoSaveCheckbox.selected)
            label(IdeBundle.message("label.inactive.timeout.sec"))
          }
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
        commentRow(IdeBundle.message("label.autosave.comment"))
      }

      for (configurable in configurables) {
        appendDslConfigurableRow(configurable)
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