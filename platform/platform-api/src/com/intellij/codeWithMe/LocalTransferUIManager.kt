// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeWithMe

import com.intellij.openapi.util.UserDataHolder
import javax.swing.JComponent

class LocalTransferUIManager: TransferUIManager {
  override fun forbidBeControlizationInLux(comp: UserDataHolder, registryKey: String) {}
  override fun forbidBeControlizationInLux(comp: JComponent, registryKey: String) {}
  override fun setWellBeControlizable(component: UserDataHolder) {}
  override fun setWellBeControlizable(component: JComponent) {}
  override fun isBeControlizationForbiddenInLux(component: JComponent) = true
  override fun isBeControlizationForbiddenInLux(component: UserDataHolder) = true
  override fun isWellBeControlizable(component: JComponent) = false
  override fun isWellBeControlizable(component: UserDataHolder) = false
}