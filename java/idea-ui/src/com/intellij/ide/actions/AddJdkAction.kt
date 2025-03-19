// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.impl.AddJdkService
import com.intellij.openapi.projectRoots.impl.ConfigureJdkService
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil

class AddJdkAction : AnAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val sdk = JavaSdk.getInstance()

    SdkConfigurationUtil.selectSdkHome(sdk) { path ->
      service<AddJdkService>().createJdkFromPath(path) {
        e.project?.service<ConfigureJdkService>()?.setProjectJdkIfNull(it, true)
      }
    }
  }
}