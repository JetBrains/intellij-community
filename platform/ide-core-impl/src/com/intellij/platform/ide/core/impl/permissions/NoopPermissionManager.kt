// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.core.impl.permissions

import com.intellij.platform.ide.core.permissions.impl.IdePermission
import com.intellij.platform.ide.core.permissions.impl.IdePermissionManager

internal class NoopPermissionManager: IdePermissionManager {
  override fun isGranted(permission: IdePermission): Boolean = true
}