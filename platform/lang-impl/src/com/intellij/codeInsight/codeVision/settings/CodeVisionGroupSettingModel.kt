// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision.settings

import com.intellij.codeInsight.hints.CodeVisionInlayVisibilityKey
import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.codeInsight.hints.InlayGroup
import com.intellij.codeInsight.hints.InlayVisibilityRequest
import com.intellij.codeInsight.hints.settings.InlayProviderSettingsModel
import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

abstract class CodeVisionGroupSettingModel(isEnabled: Boolean, id: String) : InlayProviderSettingsModel(isEnabled, id, Language.ANY) {

  final override val group: InlayGroup
    get() = InlayGroup.CODE_VISION_GROUP_NEW

  override val previewText: String? = null

  protected open val previewLanguage: Language? = null

  final override fun getCasePreview(case: ImmediateConfigurable.Case?): String? {
    return previewText
  }

  final override fun getCasePreviewLanguage(case: ImmediateConfigurable.Case?): Language? {
    return previewLanguage
  }

  final override fun getCaseDescription(case: ImmediateConfigurable.Case): String? {
    return description
  }

  final override val mainCheckBoxLabel: String
    get() = name

  final override val cases: List<ImmediateConfigurable.Case>
    get() = emptyList()

  @ApiStatus.Internal
  final override fun visibilityRequest(project: Project, case: ImmediateConfigurable.Case?): InlayVisibilityRequest {
    return InlayVisibilityRequest(project, null, CodeVisionInlayVisibilityKey(id))
  }
}