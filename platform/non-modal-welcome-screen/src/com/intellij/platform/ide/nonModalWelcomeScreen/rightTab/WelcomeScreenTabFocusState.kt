// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.nonModalWelcomeScreen.isWelcomeExperienceProjectSync
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The focus policy of the welcome tab in one project.
 *
 * Two facts live here, so the tab UI and the code that opens the tab read one owner:
 * - [selectsTabOnStartupOpen]: whether the startup open selects the tab as the current one. A file opened from the
 *   command line into the welcome project turns it off, so that file stays in front.
 * - [contentFocusEnabled]: whether activating the tab moves the input focus into its content. In a welcome project
 *   it starts off, so the passive startup open leaves the focus with the left project view (IJPL-248588), and
 *   [com.intellij.platform.ide.nonModalWelcomeScreen.WelcomeScreenProjectActivity] turns it on once the startup focus has settled. In any other project it starts on.
 *   Later user-driven activations (Esc from the project view, a click on the tab) then focus the content (IJPL-203369).
 */
@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class WelcomeScreenTabFocusState(project: Project) {
  private val selectsTabOnStartupOpenState = AtomicBoolean(true)
  private val contentFocusEnabledState = AtomicBoolean(!project.isWelcomeExperienceProjectSync())

  val selectsTabOnStartupOpen: Boolean
    get() = selectsTabOnStartupOpenState.get()

  fun preventSelectionOnStartupOpen() {
    selectsTabOnStartupOpenState.set(false)
  }

  val contentFocusEnabled: Boolean
    get() = contentFocusEnabledState.get()

  fun enableContentFocus() {
    contentFocusEnabledState.set(true)
  }

  companion object {
    fun getInstance(project: Project): WelcomeScreenTabFocusState = project.service()

    suspend fun getInstanceAsync(project: Project): WelcomeScreenTabFocusState = project.serviceAsync()
  }
}
