// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.colors

import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface SchemesPanelFactory {
  fun createSchemesPanel(options: ColorAndFontOptions): SchemesPanel
}