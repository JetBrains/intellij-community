// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.core.permissions

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface Permission {
  val id: String

  /**
   * Checks whether this particular permission is granted.
   *
   * @return true if the permission is granted, false otherwise.
   */
  fun isGranted(): Boolean
}
