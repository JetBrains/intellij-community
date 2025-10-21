// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template

import com.intellij.codeInsight.template.impl.TemplateGroup
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

/**
 * Returns an arbitrary hint for a template group which is displayed
 * as gray text near its name in the settings window.
 *
 * If there are multiple implementations loaded, their hints will
 * be sorted lexicographically and joined with `; `.
 */
@ApiStatus.Internal
@ApiStatus.OverrideOnly
interface TemplateGroupHintProvider {
  fun getHint(templateGroup: TemplateGroup): String?

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<TemplateGroupHintProvider> = ExtensionPointName.create("com.intellij.templateGroupHintProvider")
  }
}
