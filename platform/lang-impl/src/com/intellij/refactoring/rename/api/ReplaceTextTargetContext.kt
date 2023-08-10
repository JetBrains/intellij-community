// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.api

import com.intellij.model.search.SearchContext
import com.intellij.model.search.SearchContext.IN_COMMENTS
import com.intellij.model.search.SearchContext.IN_STRINGS
import java.util.*

enum class ReplaceTextTargetContext(
  internal val searchContexts: Set<SearchContext>
) {
  IN_COMMENTS_AND_STRINGS(EnumSet.of(IN_COMMENTS, IN_STRINGS)),
  IN_PLAIN_TEXT(EnumSet.of(SearchContext.IN_PLAIN_TEXT)),
  ;
}
