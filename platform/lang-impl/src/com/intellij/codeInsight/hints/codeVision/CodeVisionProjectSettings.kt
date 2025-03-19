// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.codeVision

import com.intellij.codeInsight.codeVision.CodeVisionHost
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Service(Service.Level.PROJECT)
@State(name = "CodeVisionProjectSettings", storages = [Storage("editor.xml")], category = SettingsCategory.CODE)
internal class CodeVisionProjectSettings(
  private val project: Project,
) : SimplePersistentStateComponent<CodeVisionProjectSettings.SettingsState>(SettingsState()) {

  private val lock = ReentrantLock()

  fun isEnabledForProject(): Boolean {
    lock.withLock {
      return state.isEnabledForProject
    }
  }

  fun setEnabledForProject(isEnabled: Boolean) {
    var changed = false
    lock.withLock {
      val wasEnabled = state.isEnabledForProject
      if (wasEnabled != isEnabled) {
        state.isEnabledForProject = isEnabled
        state.intIncrementModificationCount()
        changed = true
      }
    }
    if (changed) {
      forceCodeVisionPass()
    }
  }

  override fun loadState(state: SettingsState) {
    lock.withLock {
      super.loadState(state)
    }
  }

  class SettingsState : BaseState() {
    var isEnabledForProject: Boolean by property(true)
  }

  private fun forceCodeVisionPass() {
    ModificationStampUtil.clearModificationStamp()
    DaemonCodeAnalyzer.getInstance(project).restart()
    project.service<CodeVisionHost>().invalidateProviderSignal.fire(CodeVisionHost.LensInvalidateSignal(null))
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): CodeVisionProjectSettings = project.service()
  }
}
