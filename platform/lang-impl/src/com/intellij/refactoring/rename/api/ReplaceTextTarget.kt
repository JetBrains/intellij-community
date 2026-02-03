// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.api

import com.intellij.model.search.SearchRequest

/**
 * Represents a minimal piece of data and logic to be able to find and replace the potential text usages.
 *
 * Example: when renaming the Java class `Bar` from package `com.foo` to `XBar`
 * we also want to find and update fully qualified names in plain text:
 * - the [textSearchRequest].[searchString][SearchRequest.getSearchString] is `com.foo.Bar`;
 * - the [usageTextByName] function returns `com.foo.XBar`.
 *
 * @param textSearchRequest what and where to search
 * @param usageTextByName given new name computes new text to replace the found search string
 */
data class ReplaceTextTarget(
  val textSearchRequest: SearchRequest,
  val usageTextByName: UsageTextByName
)
