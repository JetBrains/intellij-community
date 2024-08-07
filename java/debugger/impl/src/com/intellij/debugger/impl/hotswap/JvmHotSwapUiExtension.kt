// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl.hotswap

import com.intellij.icons.AllIcons
import com.intellij.util.PlatformUtils
import com.intellij.xdebugger.impl.hotswap.HotSwapUiExtension
import javax.swing.Icon

private class JvmHotSwapUiExtension : HotSwapUiExtension {
  override fun isApplicable(): Boolean = PlatformUtils.isIntelliJ()

  override val hotSwapIcon: Icon
    get() = AllIcons.Actions.Rebuild
}
