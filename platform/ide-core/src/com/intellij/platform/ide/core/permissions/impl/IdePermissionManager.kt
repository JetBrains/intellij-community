// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.core.permissions.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.platform.ide.core.permissions.Permission
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class IdePermission(override val id: String): Permission {
  override fun isGranted(): Boolean {
    return IdePermissionManager.getInstance().isGranted(this)
  }
}

@ApiStatus.Internal
interface IdePermissionManager {
  fun isGranted(permission: IdePermission): Boolean

  companion object {
    fun getInstance(): IdePermissionManager = ApplicationManager.getApplication().service<IdePermissionManager>()
  }
}