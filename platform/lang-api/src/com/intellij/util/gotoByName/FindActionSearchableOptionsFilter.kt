// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.gotoByName

import com.intellij.ide.ui.search.OptionDescription
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface FindActionSearchableOptionsFilter {
  fun isAvailable(description: OptionDescription): Boolean

  companion object {
    @JvmStatic
    val EP_NAME: ExtensionPointName<FindActionSearchableOptionsFilter> = ExtensionPointName.Companion.create("com.intellij.findActionSearchableOptionsFilter")
  }
}