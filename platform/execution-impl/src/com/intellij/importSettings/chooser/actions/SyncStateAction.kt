// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.importSettings.chooser.actions

import com.intellij.icons.AllIcons
import com.intellij.importSettings.data.SettingsService
import com.intellij.importSettings.data.SyncService
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class SyncStateAction : DumbAwareAction() {
  private val settingsService = SettingsService.getInstance()
  private val syncService = settingsService.getSyncService()

  override fun displayTextInToolbar(): Boolean {
    return true
  }

  override fun actionPerformed(e: AnActionEvent) {
    when (syncService.syncState) {
      SyncService.SYNC_STATE.UNLOGGED -> {
        syncService.tryToLogin()
        return
      }
      SyncService.SYNC_STATE.GENERAL -> {
        syncService.generalSync()
        return
      }
      else -> return
    }
  }

  override fun update(e: AnActionEvent) {
    e.presentation.icon = AllIcons.Actions.Refresh
    e.presentation.isVisible = when (syncService.syncState) {
      SyncService.SYNC_STATE.UNLOGGED -> {
        e.presentation.text = "Log In to Setting Sync..."
        e.presentation.isEnabled = true
        true
      }
      SyncService.SYNC_STATE.WAINING_FOR_LOGIN -> {
        e.presentation.text = "Log In to Setting Sync..."
        e.presentation.isEnabled = false
        true
      }
      SyncService.SYNC_STATE.LOGIN_FAILED -> {
        e.presentation.text = "Log In failed..."
        e.presentation.isEnabled = false
        true
      }
      SyncService.SYNC_STATE.TURNED_OFF -> {
        e.presentation.text = "Setting Sync is Turned Off"
        e.presentation.isEnabled = false
        true
      }

      else -> {
        false
      }
    }
  }
}