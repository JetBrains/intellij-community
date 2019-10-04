// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.application.options.editor.CheckboxDescriptor
import com.intellij.application.options.editor.checkBox
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.PathChooserDialog
import com.intellij.openapi.options.BoundCompositeConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ex.ConfigurableWrapper
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.IdeUICustomization
import com.intellij.ui.layout.*
import com.intellij.util.PlatformUtils

val model = GeneralSettings.getInstance()
val myChkReopenLastProject = CheckboxDescriptor(IdeBundle.message("checkbox.reopen.last.project.on.startup", IdeUICustomization.getInstance().projectConceptName),
                                                PropertyBinding(model::isReopenLastProject, model::setReopenLastProject))
val myConfirmExit = CheckboxDescriptor(IdeBundle.message("checkbox.confirm.application.exit"),
                                       PropertyBinding(model::isConfirmExit, model::setConfirmExit))
val myShowWelcomeScreen = CheckboxDescriptor(IdeBundle.message("checkbox.show.welcome.screen"),
                                             PropertyBinding(model::isShowWelcomeScreen, model::setShowWelcomeScreen))
val myChkSyncOnFrameActivation = CheckboxDescriptor(IdeBundle.message("checkbox.synchronize.files.on.frame.activation"),
                                                    PropertyBinding(model::isSyncOnFrameActivation, model::setSyncOnFrameActivation))
val myChkSaveOnFrameDeactivation = CheckboxDescriptor(IdeBundle.message("checkbox.save.files.on.frame.deactivation"),
                                                      PropertyBinding(model::isSaveOnFrameDeactivation, model::setSaveOnFrameDeactivation))
val myChkAutoSaveIfInactive = CheckboxDescriptor(IdeBundle.message("checkbox.save.files.automatically"),
                                                 PropertyBinding(model::isAutoSaveIfInactive, model::setAutoSaveIfInactive))
val myChkUseSafeWrite = CheckboxDescriptor("Use \"safe write\" (save changes to a temporary file first)",
                                           PropertyBinding(model::isUseSafeWrite, model::setUseSafeWrite))

val allOptionDescriptors = listOf(
  myChkReopenLastProject,
  myConfirmExit,
  myChkSyncOnFrameActivation,
  myChkSaveOnFrameDeactivation,
  myChkAutoSaveIfInactive,
  myChkUseSafeWrite
).map { it.asOptionDescriptor() }

/**
 * To provide additional options in General section register implementation of {@link SearchableConfigurable} in the plugin.xml:
 * <p/>
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;<br>
 * &nbsp;&nbsp;&lt;generalOptionsProvider instance="class-name"/&gt;<br>
 * &lt;/extensions&gt;
 * <p>
 * A new instance of the specified class will be created each time then the Settings dialog is opened
 */
class GeneralSettingsConfigurable: BoundCompositeConfigurable<SearchableConfigurable>(
  IdeBundle.message("title.general"),
  "preferences.general"
), SearchableConfigurable {
  private val model = GeneralSettings.getInstance()

  override fun createPanel(): DialogPanel {
    val projectConceptName = IdeUICustomization.getInstance().projectConceptName

    return panel {
      row {
        titledRow("Startup/Shutdown") {
          row {
            checkBox(myChkReopenLastProject)
          }
          row {
            checkBox(myConfirmExit)
          }

          if (PlatformUtils.isDataGrip()) {
            row {
              checkBox(myShowWelcomeScreen)
            }
          }
        }
      }
      row {
        titledRow(IdeBundle.message("border.title.project.opening", projectConceptName.capitalize())) {
          row("Default directory:") {
            textFieldWithBrowseButton(model::getDefaultProjectDirectory, model::setDefaultProjectDirectory,
                                      fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
                                        .also { it.putUserData(PathChooserDialog.PREFER_LAST_OVER_EXPLICIT, false) },
                                      growPolicy = GrowPolicy.MEDIUM_TEXT)
              .comment("This directory is preselected in \"Open...\" and \"New | Project...\" dialogs.", 80)
          }
          buttonGroup(model::getConfirmOpenNewProject, model::setConfirmOpenNewProject) {
            row {
              radioButton(IdeBundle.message("radio.button.open.project.in.the.new.window", projectConceptName), GeneralSettings.OPEN_PROJECT_NEW_WINDOW)
            }
            row {
              radioButton(IdeBundle.message("radio.button.open.project.in.the.same.window", projectConceptName), GeneralSettings.OPEN_PROJECT_SAME_WINDOW)
            }
            row {
              radioButton(IdeBundle.message("radio.button.confirm.window.to.open.project.in", projectConceptName), GeneralSettings.OPEN_PROJECT_ASK)
            }
          }
        }
      }
      row {
        titledRow("Synchronization") {
          row {
            checkBox(myChkSyncOnFrameActivation)
          }
          row {
            checkBox(myChkSaveOnFrameDeactivation)
          }
          row {
            cell(isFullWidth = true) {
              val autoSaveCheckbox = checkBox(myChkAutoSaveIfInactive)
              intTextField(model::getInactiveTimeout, model::setInactiveTimeout, columns = 4).enableIf(autoSaveCheckbox.selected)
              label(IdeBundle.message("label.inactive.timeout.sec"))
            }
          }
          row {
            checkBox(myChkUseSafeWrite)
          }
        }
      }
      row {
        titledRow(IdeBundle.message("group.settings.process.tab.close")) {
          buttonGroup(model::getProcessCloseConfirmation, model::setProcessCloseConfirmation) {
            row {
              radioButton(IdeBundle.message("radio.process.close.terminate"), GeneralSettings.ProcessCloseConfirmation.TERMINATE)
            }
            row {
              radioButton(IdeBundle.message("radio.process.close.disaconnect"), GeneralSettings.ProcessCloseConfirmation.DISCONNECT)
            }
            row {
              radioButton(IdeBundle.message("radio.process.close.ask"), GeneralSettings.ProcessCloseConfirmation.ASK)
            }
          }
        }
      }

      for (configurable in configurables) {
        row {
          configurable.createComponent()?.invoke()
        }
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