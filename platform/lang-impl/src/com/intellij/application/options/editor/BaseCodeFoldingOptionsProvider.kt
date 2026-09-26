// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.editor

import com.intellij.codeInsight.folding.CodeFoldingSettings
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.options.BeanConfigurable
import com.intellij.ui.dsl.builder.TopGap
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class BaseCodeFoldingOptionsProvider : BeanConfigurable<CodeFoldingSettings>(
  CodeFoldingSettings.getInstance(), ApplicationBundle.message("title.general")), CodeFoldingOptionsProvider {

  init {
    groupTopGap = TopGap.NONE

    checkBox(ApplicationBundle.message("checkbox.collapse.file.header"), instance::COLLAPSE_FILE_HEADER)
    checkBox(ApplicationBundle.message("checkbox.collapse.title.imports"), instance::COLLAPSE_IMPORTS)
    checkBox(ApplicationBundle.message("checkbox.collapse.javadoc.comments"), instance::COLLAPSE_DOC_COMMENTS)
    checkBox(ApplicationBundle.message("checkbox.collapse.method.bodies"), instance::COLLAPSE_METHODS)
    checkBox(ApplicationBundle.message("checkbox.collapse.custom.folding.regions"), instance::COLLAPSE_CUSTOM_FOLDING_REGIONS)
  }
}
