// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.editor

import com.intellij.openapi.options.ConfigurableEP
import com.intellij.openapi.options.UiDslUnnamedConfigurable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class InlineDocAdditionalHandlerEP : ConfigurableEP<InlineDocsAdditionalConfigurable>() {
}

@ApiStatus.Internal
abstract class InlineDocsAdditionalConfigurable : UiDslUnnamedConfigurable.Simple() {
  abstract fun getParticipatingSettingsFlags(): Array<Boolean>
}