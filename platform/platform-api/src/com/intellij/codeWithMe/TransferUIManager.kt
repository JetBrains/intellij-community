// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeWithMe

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.util.UserDataHolder
import javax.swing.JComponent

interface TransferUIManager {
  companion object {
    @JvmStatic
    fun getInstance(): TransferUIManager = ApplicationManager.getApplication().service()
  }
  fun forbidBeControlizationInLux(comp: UserDataHolder, registryKey: String)
  fun forbidBeControlizationInLux(comp: JComponent, registryKey: String)
  fun setWellBeControlizable(component: UserDataHolder)
  fun setWellBeControlizable(component: JComponent)
  fun isBeControlizationForbiddenInLux(component: JComponent): Boolean
  fun isBeControlizationForbiddenInLux(component: UserDataHolder): Boolean
  fun isWellBeControlizable(component: JComponent): Boolean
  fun isWellBeControlizable(component: UserDataHolder): Boolean
}