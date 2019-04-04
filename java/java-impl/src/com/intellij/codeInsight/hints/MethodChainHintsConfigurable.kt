// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.options.BeanConfigurable

class MethodChainHintsConfigurable : BeanConfigurable<CodeInsightSettings>() {
  init {
    val settings = CodeInsightSettings.getInstance()

    checkBox(ApplicationBundle.message("editor.appearance.show.chain.call.type.hints"), settings::SHOW_METHOD_CHAIN_TYPES_INLINE)
  }

  override fun apply() {
    super.apply()
    MethodChainHintsPassFactory.modificationStampHolder.forceHintsUpdateOnNextPass()
  }
}