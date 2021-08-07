// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.layout.*
import com.intellij.util.ui.JBUI

class EmptyModuleStep : NewModuleStep<EmptySettings>() {
  override var settings = EmptySettings()

  override var panel: DialogPanel = panel {
    nameAndPath()
    gitCheckbox()
  }.withBorder(JBUI.Borders.empty(10, 10))
}

class EmptySettings