// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.ide.IdeBundle
import com.intellij.openapi.components.Service
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.impl.IdeFrameDecorator
import com.intellij.openapi.wm.impl.MERGE_MAIN_MENU_WITH_WINDOW_TITLE_PROPERTY
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomWindowHeaderUtil
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomWindowHeaderUtil.HideNativeLinuxTitleNotSupportedReason
import com.intellij.openapi.wm.impl.isMergeMainMenuWithWindowTitleOverridden

@Suppress("unused")
@Service(Service.Level.APP)
internal class MergeMainMenuSettingProvider {
  var mergeMainMenu: Boolean
    get() = UISettings.getInstance().mergeMainMenuWithWindowTitle
    set(value) {
      UISettings.getInstance().mergeMainMenuWithWindowTitle = value
    }

  fun getMergeMainMenuDescription(): String? {
    return if (SystemInfoRt.isWindows && isMergeMainMenuWithWindowTitleOverridden) {
      IdeBundle.message("option.is.overridden.by.jvm.property", MERGE_MAIN_MENU_WITH_WINDOW_TITLE_PROPERTY)
    }
    else if (SystemInfo.isUnix && !SystemInfo.isMac && !CustomWindowHeaderUtil.hideNativeLinuxTitleSupported) {
      when (CustomWindowHeaderUtil.hideNativeLinuxTitleNotSupportedReason) {
        HideNativeLinuxTitleNotSupportedReason.INCOMPATIBLE_JBR -> IdeBundle.message("hide.native.linux.title.not.supported.incompatible.jbr")
        HideNativeLinuxTitleNotSupportedReason.WAYLAND_OR_XTOOLKIT_REQUIRED -> IdeBundle.message("hide.native.linux.title.not.supported.wayland.or.xtoolkit.required")
        HideNativeLinuxTitleNotSupportedReason.WSL_NOT_SUPPORTED -> IdeBundle.message("hide.native.linux.title.not.supported.wsl")
        HideNativeLinuxTitleNotSupportedReason.TILING_WM_NOT_SUPPORTED -> IdeBundle.message("hide.native.linux.title.not.supported.tiling.wm")
        HideNativeLinuxTitleNotSupportedReason.UNDEFINED_DESKTOP_NOT_SUPPORTED -> IdeBundle.message("hide.native.linux.title.not.supported.undefined.desktop")
        null -> null
      }
    }
    else {
      IdeBundle.message("ide.restart.required.comment")
    }
  }

  fun isMergeMainMenuEnabled(): Boolean {
    if (SystemInfo.isUnix && !SystemInfo.isMac && !CustomWindowHeaderUtil.hideNativeLinuxTitleSupported) {
      return false
    }
    return !(SystemInfoRt.isWindows && isMergeMainMenuWithWindowTitleOverridden)
  }

  fun isMergeMainMenuVisible(): Boolean {
    return SystemInfoRt.isWindows && IdeFrameDecorator.isCustomDecorationAvailable || CustomWindowHeaderUtil.hideNativeLinuxTitleAvailable
    }
}