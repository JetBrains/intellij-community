// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl.hotswap

import com.intellij.configurationStore.saveSettingsForRemoteDevelopment
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.util.PlatformUtils
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.impl.hotswap.HotSwapUiExtension
import icons.PlatformDebuggerImplIcons
import javax.swing.Icon

private class JvmHotSwapUiExtension : HotSwapUiExtension {
  override fun isApplicable(): Boolean = PlatformUtils.isIntelliJ() || PlatformUtils.isJetBrainsClient()
  override fun showFloatingToolbar() = DebuggerSettings.getInstance().HOTSWAP_SHOW_FLOATING_BUTTON

  override val hotSwapIcon: Icon
    get() = PlatformDebuggerImplIcons.Actions.DebuggerSync

  override fun popupMenuActions() = DefaultActionGroup(ToggleShowButtonAction())
}

private class ToggleShowButtonAction : DumbAwareToggleAction(XDebuggerBundle.message("label.debugger.hotswap.option.suggest.in.editor")) {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT
  override fun isSelected(e: AnActionEvent) = DebuggerSettings.getInstance().HOTSWAP_SHOW_FLOATING_BUTTON
  override fun setSelected(e: AnActionEvent, state: Boolean) {
    DebuggerSettings.getInstance().HOTSWAP_SHOW_FLOATING_BUTTON = state
    saveSettingsForRemoteDevelopment(ApplicationManager.getApplication())
  }
}
