// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.core.permissions

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus

/**
 * Verifies that all provided permissions are granted.
 * For [com.intellij.openapi.actionSystem.AnAction], [RequiresPermissions] should be overridden instead of checking permissions manually.
 *
 * @param permissions permissions to check
 * @throws PermissionDeniedException if one or more permissions are not granted.
 */
@ApiStatus.Experimental
@Throws(PermissionDeniedException::class)
fun checkPermissionsGranted(vararg permissions: Permission) {
  if (!Registry.`is`("ide.permissions.api.enabled")) return

  val unsatisfied = permissions.filter { permission ->
    !permission.isGranted()
  }
  if (unsatisfied.isNotEmpty()) {
    throw PermissionDeniedException(unsatisfied)
  }
}