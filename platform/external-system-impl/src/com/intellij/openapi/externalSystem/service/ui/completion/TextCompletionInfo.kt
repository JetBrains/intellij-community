// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.ui.completion

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.Nls
import javax.swing.Icon

data class TextCompletionInfo(
  val text: @NlsSafe String,
  val description: @Nls String? = null,
  val icon: Icon? = null)