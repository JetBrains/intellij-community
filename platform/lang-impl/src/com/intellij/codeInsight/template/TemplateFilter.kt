// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template

import com.intellij.codeInsight.template.impl.TemplateImpl
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

/**
 * Returns whether to show a live template in the settings window based
 * on arbitrary criteria (all data from [TemplateImpl]).
 *
 * If there are multiple implementations loaded, all should return
 * `true` in order to show the template.
 */
@ApiStatus.Internal
@ApiStatus.OverrideOnly
interface TemplateFilter {
  fun accept(template: TemplateImpl): Boolean

  companion object {
    @JvmStatic
    fun globalAccept(template: TemplateImpl): Boolean {
      for (filter in EP_NAME.extensionList) {
        if (!filter.accept(template)) return false
      }
      return true
    }

    @JvmField
    val EP_NAME: ExtensionPointName<TemplateFilter> = ExtensionPointName.create("com.intellij.templateFilter")
  }
}
