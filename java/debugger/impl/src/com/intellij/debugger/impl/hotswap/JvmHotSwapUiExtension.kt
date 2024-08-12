// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl.hotswap

import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.util.PlatformUtils
import com.intellij.xdebugger.impl.hotswap.HotSwapUiExtension
import icons.PlatformDebuggerImplIcons
import javax.swing.Icon

private class JvmHotSwapUiExtension : HotSwapUiExtension {
  override fun isApplicable(): Boolean = PlatformUtils.isIntelliJ()
  override fun showFloatingToolbar() = DebuggerSettings.getInstance().HOTSWAP_SHOW_FLOATING_BUTTON

  override val hotSwapIcon: Icon
    get() = PlatformDebuggerImplIcons.Actions.DebuggerSync
}
