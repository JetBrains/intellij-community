// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.suggestion

import com.intellij.openapi.util.UserDataHolderBase

internal interface InlineCompletionPresentableVariant {
  // TODO make index: raw and actual
  val index: Int

  // TODO do we need this?
  val data: UserDataHolderBase
}
