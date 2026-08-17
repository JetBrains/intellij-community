// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui

import com.intellij.ide.PowerSaveMode
import com.intellij.ide.RemoteDesktopService
import com.intellij.openapi.util.registry.Registry.Companion.`is`

object DrawUtil {

  @JvmStatic
  fun isSimplifiedUI(): Boolean {
    return `is`("ui.simplified", false) ||
           RemoteDesktopService.isRemoteSession() ||
           PowerSaveMode.isEnabled()
  }
}
