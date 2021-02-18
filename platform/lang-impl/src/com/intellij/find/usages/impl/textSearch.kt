// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:ApiStatus.Internal

package com.intellij.find.usages.impl

import com.intellij.find.usages.api.PsiUsage
import com.intellij.find.usages.api.SearchTarget
import com.intellij.find.usages.api.Usage
import com.intellij.model.search.SearchContext
import com.intellij.model.search.impl.buildTextQuery
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.search.SearchScope
import com.intellij.util.Query
import org.jetbrains.annotations.ApiStatus
import java.util.*

internal fun SearchTarget.hasTextSearchStrings(): Boolean = textSearchStrings.isNotEmpty()

internal fun buildTextQuery(project: Project, searchString: String, searchScope: SearchScope): Query<out Usage> {
  val length = searchString.length
  return buildTextQuery(
    project, searchString, searchScope,
    searchContexts = EnumSet.of(SearchContext.IN_COMMENTS, SearchContext.IN_STRINGS, SearchContext.IN_PLAIN_TEXT)
  ).mapping { occurrence ->
    PlainTextUsage(PsiUsage.textUsage(occurrence.element, TextRange.from(occurrence.offsetInElement, length)))
  }
}
