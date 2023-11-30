// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.dialog.uiBlocks

import com.intellij.openapi.util.NlsContexts

data class CheckBoxItemData(@NlsContexts.Checkbox val label: String,
                            val jsonElementName: String) {
  internal var property: Boolean = false
}
