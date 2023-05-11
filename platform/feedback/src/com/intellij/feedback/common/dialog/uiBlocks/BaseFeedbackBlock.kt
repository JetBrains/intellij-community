// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.common.dialog.uiBlocks

import com.intellij.ui.dsl.builder.Panel

abstract class BaseFeedbackBlock {

  abstract fun addToPanel(panel: Panel)

  open fun collectBlockTextDescription(stringBuilder: StringBuilder) {
    // Nothing to add
  }
}