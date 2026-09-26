// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.codeInsight.lookup.LookupElement
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

//TODO IJPL-207762 mark experimental
@ApiStatus.Internal
@Serializable
object NoOpFrontendFriendlyInsertHandler : FrontendFriendlyInsertHandler {
  override fun handleInsert(context: InsertionContext, item: LookupElement) {}
}