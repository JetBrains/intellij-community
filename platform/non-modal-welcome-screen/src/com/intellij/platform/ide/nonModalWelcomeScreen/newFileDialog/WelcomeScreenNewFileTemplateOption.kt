// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.newFileDialog

import com.intellij.openapi.util.NlsContexts
import javax.swing.Icon

data class WelcomeScreenNewFileTemplateOption(
  val templateName: String,
  @NlsContexts.Label val displayName: String,
  val icon: Icon? = null,
)
