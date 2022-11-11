// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.miniMap.actions

import com.intellij.ide.miniMap.settings.MiniMapSettings
import com.intellij.ide.miniMap.utils.MiniMessagesBundle
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class MoveMiniMapAction : AnAction(if (MiniMapSettings.getInstance().state.rightAligned) MiniMessagesBundle.message("action.moveLeft")
                                   else MiniMessagesBundle.message("action.moveRight")) {
  override fun isDumbAware() = true
  override fun actionPerformed(e: AnActionEvent) {
    val settings = MiniMapSettings.getInstance()
    settings.state.rightAligned = !settings.state.rightAligned
    settings.settingsChangeCallback.notify(MiniMapSettings.SettingsChangeType.WithUiRebuild)
  }
}