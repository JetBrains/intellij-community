// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.ConfigurableEP
import com.intellij.openapi.options.UnnamedConfigurable
import org.jetbrains.annotations.ApiStatus

/**
 * Extension point for additional options in Editor | Code Completion | Machine Learning-Assisted Completion section
 */
@ApiStatus.Internal
object MLCodeCompletionConfigurableEP : ConfigurableEP<UnnamedConfigurable>() {
  val EP_NAME = ExtensionPointName.create<MLCodeCompletionConfigurableEP>("com.intellij.mlCodeCompletionConfigurable")
}
