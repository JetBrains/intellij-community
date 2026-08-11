// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.options.advanced.AdvancedSettingsChangeListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.CloseProjectWindowHelper
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeScreenPreventWelcomeTabFocusService
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeScreenRightTab
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeScreenRightTabVirtualFile
import com.intellij.util.application
import com.intellij.util.asSafely
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class WelcomeScreenProjectActivity : ProjectActivity {
  init {
    val app = ApplicationManager.getApplication()
    val isNotAvailable = app.isCommandLine || app.isHeadlessEnvironment || app.isUnitTestMode
    if (isNotAvailable) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    if (project.isWelcomeExperienceProject()) {
      dropModalWelcomeScreenOnClose(project)
      subscribeToWelcomeScreenTabClose(project)
      focusLeftProjectView(project)
    }
  }

  private suspend fun focusLeftProjectView(project: Project) {
    if (!project.serviceAsync<WelcomeScreenPreventWelcomeTabFocusService>().isAllowedFocusOnWelcomeTab()) {
      return
    }
    val toolWindowManager = project.serviceAsync<ToolWindowManager>()
    withContext(Dispatchers.EDT) {
      toolWindowManager.getToolWindow(ToolWindowId.PROJECT_VIEW)?.activate(null)
    }
    // Re-enable focusing the welcome tab content only after the focus transfer to the project view has been
    // processed (a later EDT event), so the passive startup open does not steal focus first (avoids the IJPL-248588
    // flicker), while later user-driven activations (Esc from the project view, clicking the tab) still focus the
    // tab content (IJPL-203369).
    withContext(Dispatchers.EDT) {
      WelcomeScreenRightTab.getInstance(project)?.enableContentFocus()
    }
  }

  private suspend fun dropModalWelcomeScreenOnClose(project: Project) {
    if (isNonModalWelcomeScreenEnabled) {
      CloseProjectWindowHelper.SHOW_WELCOME_FRAME_FOR_PROJECT.set(project, false)
    }
    subscribeToSettingsChanges(project)
  }

  private suspend fun subscribeToSettingsChanges(project: Project) {
    application
      .messageBus
      .connect(WelcomeScreenProjectScopeHolder.getInstanceAsync(project).coroutineScope)
      .subscribe(AdvancedSettingsChangeListener.TOPIC,
                 object : AdvancedSettingsChangeListener {
                   override fun advancedSettingChanged(id: String, oldValue: Any, newValue: Any) {
                     if (id == NON_MODAL_WELCOME_SCREEN_SETTING_ID) {
                       val welcomeScreenEnabled = newValue.asSafely<Boolean>() ?: return
                       CloseProjectWindowHelper.SHOW_WELCOME_FRAME_FOR_PROJECT.set(project, !welcomeScreenEnabled)
                     }
                     if (id == NON_MODAL_WELCOME_SCREEN_SETTING_ID) {
                       val welcomeScreenTabEnabled = newValue.asSafely<Boolean>() ?: return
                       if (welcomeScreenTabEnabled) {
                         WelcomeScreenTabUsageCollector.logWelcomeScreenTabEnabled()
                       } else {
                         WelcomeScreenTabUsageCollector.logWelcomeScreenTabDisabled()
                       }
                     }
                   }
                 })
  }

  private suspend fun subscribeToWelcomeScreenTabClose(project: Project) {
    project
      .messageBus
      .connect(WelcomeScreenProjectScopeHolder.getInstanceAsync(project).coroutineScope)
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
