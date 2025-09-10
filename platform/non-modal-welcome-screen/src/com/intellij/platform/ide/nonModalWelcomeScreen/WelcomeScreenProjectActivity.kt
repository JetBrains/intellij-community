// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen

import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.options.advanced.AdvancedSettingsChangeListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider.Companion.isWelcomeScreenProject
import com.intellij.openapi.wm.impl.CloseProjectWindowHelper
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeScreenRightTab
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeScreenRightTabVirtualFile
import com.intellij.util.application
import com.intellij.util.asSafely
import kotlinx.coroutines.coroutineScope

internal class WelcomeScreenProjectActivity : ProjectActivity {
  init {
    val isNotAvailable = application.isCommandLine || application.isHeadlessEnvironment || application.isUnitTestMode
    if (isNotAvailable) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    if (isWelcomeScreenProject(project)) {
      dropModalWelcomeScreenOnClose(project)
      subscribeToWelcomeScreenTabClose(project)
      WelcomeScreenRightTab.show(project)
    }
  }
}

private suspend fun dropModalWelcomeScreenOnClose(project: Project) {
  CloseProjectWindowHelper.SHOW_WELCOME_FRAME_FOR_PROJECT.set(project, !isNonModalWelcomeScreenEnabled)
  subscribeToSettingsChanges(project)
}

private suspend fun subscribeToSettingsChanges(project: Project) {
  coroutineScope {
    application
      .messageBus
      .connect(this@coroutineScope)
      .subscribe(AdvancedSettingsChangeListener.TOPIC,
                 object : AdvancedSettingsChangeListener {
                   override fun advancedSettingChanged(id: String, oldValue: Any, newValue: Any) {
                     if (id == NON_MODAL_WELCOME_SCREEN_SETTING_ID) {
                       val welcomeScreenEnabled = newValue.asSafely<Boolean>() ?: return
                       CloseProjectWindowHelper.SHOW_WELCOME_FRAME_FOR_PROJECT.set(project, !welcomeScreenEnabled)
                     }
                     if (id == NON_MODAL_WELCOME_SCREEN_SETTING_ID) {
                       val welcomeScreenTabEnabled = newValue.asSafely<Boolean>() ?: return
                       if (!welcomeScreenTabEnabled) {
                         WelcomeScreenTabUsageCollector.logWelcomeScreenTabDisabled()
                       }
                     }
                   }
                 })
  }
}

private suspend fun subscribeToWelcomeScreenTabClose(project: Project) {
  coroutineScope {
    project
      .messageBus
      .connect(this@coroutineScope)
      .subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER,
                 object : FileEditorManagerListener {
                   override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
                     if (file is WelcomeScreenRightTabVirtualFile) {
                       WelcomeScreenTabUsageCollector.logWelcomeScreenTabClosed()
                     }
                   }
                 })
  }
}
