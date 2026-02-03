// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.core.permissions

import org.jetbrains.annotations.ApiStatus

/**
 * For [com.intellij.openapi.actionSystem.AnAction], [checkPermissionsGranted]
 * will be called on provided permissions during action update, and before its execution.
 */
@ApiStatus.Experimental
interface RequiresPermissions {
  fun getRequiredPermissions(): Collection<Permission>
}