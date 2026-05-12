// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util

import com.intellij.frontend.FrontendApplicationInfo
import com.intellij.frontend.FrontendType
import com.intellij.idea.AppMode
import com.intellij.openapi.util.registry.RegistryManager
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

private const val SPLIT_POPUP_REGISTRY_KEY: @NonNls String = "frontend.structure.popup"
private const val SPLIT_POPUP_REMOTE_DEV_REGISTRY_KEY: @NonNls String = "remoteDev.frontend.structure.popup"

@ApiStatus.Internal
object FileStructureUtil {
  @JvmStatic
  fun isSplitPopupEnabled(): Boolean {
    val registry = RegistryManager.getInstance()
    val isController = (FrontendApplicationInfo.getFrontendType() as? FrontendType.Remote)?.isController() == true
    val isRemoteDevelopment = AppMode.isRemoteDevHost() || isController
    return registry.`is`(SPLIT_POPUP_REGISTRY_KEY) ||
           (isRemoteDevelopment && registry.`is`(SPLIT_POPUP_REMOTE_DEV_REGISTRY_KEY))
  }
}
