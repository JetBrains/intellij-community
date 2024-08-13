// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl.hotswap

import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.PlatformUtils
import com.intellij.xdebugger.impl.hotswap.HotSwapUiExtension
import icons.PlatformDebuggerImplIcons
import javax.swing.Icon

private class JvmHotSwapUiExtension : HotSwapUiExtension {
  override fun isApplicable(): Boolean = PlatformUtils.isIntelliJ()
  override fun showFloatingToolbar() = DebuggerSettings.getInstance().HOTSWAP_SHOW_FLOATING_BUTTON
  override val successStatusLocation get() =
    if (Registry.`is`("debugger.hotswap.show.ide.popup")) {
      HotSwapUiExtension.SuccessStatusLocation.IDE_POPUP
    } else {
      HotSwapUiExtension.SuccessStatusLocation.NOTIFICATION
    }

  override val hotSwapIcon: Icon
    get() = PlatformDebuggerImplIcons.Actions.DebuggerSync
}
