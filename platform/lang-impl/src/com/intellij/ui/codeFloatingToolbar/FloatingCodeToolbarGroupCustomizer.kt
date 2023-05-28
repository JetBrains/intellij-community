// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.codeFloatingToolbar

import com.intellij.ide.ui.customization.CustomizableActionGroupProvider
import com.intellij.idea.ActionsBundle

private class FloatingCodeToolbarGroupCustomizer: CustomizableActionGroupProvider() {
  override fun registerGroups(registrar: CustomizableActionGroupRegistrar) {
    registrar.addCustomizableActionGroup(
      "Floating.CodeToolbar",
      ActionsBundle.message("group.Floating.CodeToolbar.text")
    )
  }
}
