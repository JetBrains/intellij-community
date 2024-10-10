// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.help.impl

import com.intellij.openapi.help.KeymapHelpIdPresenter
import com.intellij.openapi.keymap.Keymap

class KeymapHelpIdPresenterImpl : KeymapHelpIdPresenter {
  override fun getKeymapIdForHelp(keymap: Keymap): String {
    // Presentable name is currently used as keymap ID on the IntelliJ docs side. See IDEA-276358
    return keymap.presentableName
  }
}