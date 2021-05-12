// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.ui.completetion

import com.intellij.openapi.util.NlsSafe
import javax.swing.Icon

data class TextCompletionInfo(
  val text: @NlsSafe String,
  val icon: Icon? = null,
  val description: @NlsSafe String? = null) {
}