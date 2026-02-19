// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

/**
 * Allows to remap LaF ids for migration purposes.
 */
@ApiStatus.Internal
interface UiThemeRemapper {
  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<UiThemeRemapper> = ExtensionPointName("com.intellij.themeRemapper")
  }

  /**
   * Maps an old LaF id to a new one.
   */
  fun mapLaFId(id: String): String?
}
