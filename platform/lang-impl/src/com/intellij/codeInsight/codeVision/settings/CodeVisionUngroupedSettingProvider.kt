// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.codeVision.settings

import com.intellij.codeInsight.codeVision.CodeVisionMessageBundle
import org.jetbrains.annotations.Nls

/**
 * Uses to create dummy settings for [com.intellij.codeInsight.codeVision.CodeVisionProvider] if no [CodeVisionGroupSettingProvider] were presented
 */
internal class CodeVisionUngroupedSettingProvider(
  override val groupId: String,
  @Nls private val providerName: String,
) : CodeVisionGroupSettingProvider {
  @get:Nls
  override val groupName: String
    get() = CodeVisionMessageBundle.messageOrNull("codeLens.${groupId}.name") ?: providerName
}