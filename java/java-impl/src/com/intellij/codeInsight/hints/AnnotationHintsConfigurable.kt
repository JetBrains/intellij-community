// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.application.options.editor.CodeFoldingOptionsProvider
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.options.BeanConfigurable

/**
 * @author egor
 */
class AnnotationHintsConfigurable : BeanConfigurable<CodeInsightSettings>(CodeInsightSettings.getInstance()), CodeFoldingOptionsProvider {
  init {
    val settings = instance

    checkBox(ApplicationBundle.message("editor.appearance.show.external.annotations"),
             { settings.SHOW_EXTERNAL_ANNOTATIONS_INLINE },
             { v -> settings.SHOW_EXTERNAL_ANNOTATIONS_INLINE = v })
    checkBox(ApplicationBundle.message("editor.appearance.show.inferred.annotations"),
             { settings.SHOW_INFERRED_ANNOTATIONS_INLINE },
             { v -> settings.SHOW_INFERRED_ANNOTATIONS_INLINE = v })
  }

  override fun apply() {
    super.apply()
    AnnotationHintsPassFactory.modificationStampHolder.forceHintsUpdateOnNextPass()
  }
}
