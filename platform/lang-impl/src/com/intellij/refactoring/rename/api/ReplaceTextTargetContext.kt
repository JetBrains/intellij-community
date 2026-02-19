// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename.api

import com.intellij.model.search.SearchContext
import com.intellij.model.search.SearchContext.inComments
import com.intellij.model.search.SearchContext.inStrings

enum class ReplaceTextTargetContext(
  internal val searchContexts: Set<SearchContext>
) {
  IN_COMMENTS_AND_STRINGS(setOf(inComments(), inStrings())),
  IN_PLAIN_TEXT(setOf(SearchContext.inPlainText())),
  ;
}
