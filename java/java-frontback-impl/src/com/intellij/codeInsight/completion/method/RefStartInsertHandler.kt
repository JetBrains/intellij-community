// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.method

import com.intellij.codeInsight.completion.FrontendFriendlyInsertHandler
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
class RefStartInsertHandler : InsertHandler<LookupElement>, FrontendFriendlyInsertHandler {
  override fun handleInsert(context: InsertionContext, item: LookupElement) {
    JavaMethodCallInsertHandlerHelper.installRefStartKey(context, item)
  }
}