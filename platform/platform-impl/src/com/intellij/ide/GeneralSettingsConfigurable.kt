// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.application.options.editor.CheckboxDescriptor
import com.intellij.application.options.editor.checkBox
import com.intellij.ide.ui.search.BooleanOptionDescription
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
import com.intellij.util.PlatformUtils

private val model: GeneralSettings
  get() = GeneralSettings.getInstance()

private val myChkReopenLastProject: CheckboxDescriptor
  get() = CheckboxDescriptor(IdeUICustomization.getInstance().projectMessage("checkbox.reopen.last.project.on.startup"), model::isReopenLastProject)
private val myConfirmExit: CheckboxDescriptor
  get() = CheckboxDescriptor(IdeBundle.message("checkbox.confirm.application.exit"), model::isConfirmExit)
private val myChkSyncOnFrameActivation
  get() = CheckboxDescriptor(IdeBundle.message("checkbox.synchronize.files.on.frame.activation"), model::isSyncOnFrameActivation)
private val myChkSyncInBackground
  get() = CheckboxDescriptor(IdeBundle.message("checkbox.synchronize.files.in.background"), model::isBackgroundSync)
private val myChkSaveOnFrameDeactivation
  get() = CheckboxDescriptor(IdeBundle.message("checkbox.save.files.on.frame.deactivation"), model::isSaveOnFrameDeactivation)
private val myChkAutoSaveIfInactive
  get() = CheckboxDescriptor(IdeBundle.message("checkbox.save.files.automatically"), model::isAutoSaveIfInactive)
private val myChkUseSafeWrite
  get() = CheckboxDescriptor(IdeBundle.message("checkbox.safe.write"), model::isUseSafeWrite)

internal val allOptionDescriptors: List<BooleanOptionDescription>
  get() =
    listOf(
      myChkReopenLastProject,
      myConfirmExit,
      myChkSyncOnFrameActivation,
      myChkSyncInBackground,
      myChkSaveOnFrameDeactivation,
      myChkAutoSaveIfInactive,
      myChkUseSafeWrite
    )
    .map(CheckboxDescriptor::asUiOptionDescriptor)

/**
 * To provide additional options in General section register implementation of [SearchableConfigurable] in the 'plugin.xml':
 * ```
 * <extensions defaultExtensionNs="com.intellij">
 *   <generalOptionsProvider instance="class-name"/>
 * </extensions>
 * ```
 * A new instance of the specified class will be created each time then the Settings dialog is opened.
 */
@Suppress("unused")
private class GeneralSettingsConfigurable :
  BoundCompositeSearchableConfigurable<SearchableConfigurable>(IdeBundle.message("title.general"), "preferences.general"),
  SearchableConfigurable
{
  private val model = GeneralSettings.getInstance().state

  override fun createPanel(): DialogPanel =
    panel {
      row {
        checkBox(myConfirmExit)
      }

      buttonsGroup {
        row(IdeBundle.message("group.settings.process.tab.close")) {
          radioButton(IdeBundle.message("radio.process.close.terminate"), ProcessCloseConfirmation.TERMINATE)
          radioButton(IdeBundle.message("radio.process.close.disconnect"), ProcessCloseConfirmation.DISCONNECT)
          radioButton(IdeBundle.message("radio.process.close.ask"), ProcessCloseConfirmation.ASK)
        }
      }.bind(model::processCloseConfirmation) { model.processCloseConfirmation = it }

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
        }.bind(getter = {  model.confirmOpenNewProject2 ?: GeneralSettings.defaultConfirmNewProject()  }, setter = { model.confirmOpenNewProject2 = it })

        row(IdeUICustomization.getInstance().projectMessage("settings.general.default.directory")) {
          textFieldWithBrowseButton(fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
                                      .also { it.putUserData(PathChooserDialog.PREFER_LAST_OVER_EXPLICIT, false) })
            .bindText(GeneralLocalSettings.getInstance()::defaultProjectDirectory)
            .columns(COLUMNS_MEDIUM)
            .comment(IdeBundle.message("settings.general.directory.preselected"), 80)
        }
      }

      group(IdeBundle.message("settings.general.autosave")) {
        row {
          val autoSaveCheckbox = checkBox(myChkAutoSaveIfInactive).gap(RightGap.SMALL)
          intTextField(GeneralSettings.SAVE_FILES_AFTER_IDLE_SEC.asRange())
            .bindIntText(model::inactiveTimeout) { model.inactiveTimeout = it }
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
        }.bottomGap(BottomGap.SMALL)
        buttonsGroup(IdeBundle.message("settings.general.synchronization")) {
          row {
            checkBox(myChkSyncOnFrameActivation)
          }
          row {
            checkBox(myChkSyncInBackground)
          }
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

  override fun getId(): String = helpTopic!!

  override fun createConfigurables(): List<SearchableConfigurable> = ConfigurableWrapper.createConfigurables(EP_NAME)
}

private val EP_NAME = ExtensionPointName<GeneralSettingsConfigurableEP>("com.intellij.generalOptionsProvider")
