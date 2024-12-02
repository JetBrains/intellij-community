// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.options

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.ConfigurableEP
import com.intellij.openapi.options.UnnamedConfigurable
import org.jetbrains.annotations.ApiStatus

class InlineCompletionConfigurableEP @ApiStatus.Internal constructor(): ConfigurableEP<UnnamedConfigurable>() {

  companion object {
    @ApiStatus.Internal
    @get:ApiStatus.Internal
    val EP_NAME: ExtensionPointName<InlineCompletionConfigurableEP> = ExtensionPointName("com.intellij.inlineCompletionConfigurable")
  }
}
