// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class TemplateResultListener(
  private val resultConsumer: (TemplateResult) -> Unit
) : TemplateEditingAdapter() {

  @ApiStatus.Internal
  enum class TemplateResult {
    Canceled,
    BrokenOff,
    Finished,
  }

  override fun templateCancelled(template: Template) {
    resultConsumer(TemplateResult.Canceled)
  }

  override fun templateFinished(template: Template, brokenOff: Boolean) {
    if (brokenOff) {
      resultConsumer(TemplateResult.BrokenOff)
    }
    else {
      resultConsumer(TemplateResult.Finished)
    }
  }
}
