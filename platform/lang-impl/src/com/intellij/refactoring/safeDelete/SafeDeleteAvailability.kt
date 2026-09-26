// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.safeDelete

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface SafeDeleteAvailability {
  fun isAvailable(): Boolean

  companion object {
    val EP_NAME: ExtensionPointName<SafeDeleteAvailability> =
      ExtensionPointName.create("com.intellij.refactoring.safeDeleteAvailability")

    @JvmStatic
    fun isSafeDeleteAvailable(): Boolean = EP_NAME.extensionList.all { it.isAvailable() }
  }
}
