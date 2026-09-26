// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import java.awt.Component

@ApiStatus.Internal
interface FocusedComponentProvider {
  fun getFocusedComponent(): Component?

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<FocusedComponentProvider> = ExtensionPointName.create("com.intellij.focusedComponentProvider")

    @JvmStatic
    fun findFocusedComponent(): Component? {
      for (provider in EP_NAME.extensionList) {
        val component = provider.getFocusedComponent()
        if (component != null) return component
      }
      return null
    }
  }
}
