// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search

import com.intellij.psi.search.SearchScope

internal data class WordRequestInfoImpl internal constructor(
  private val word: String,
  private val searchScope: SearchScope,
  private val caseSensitive: Boolean,
  private val searchContext: Short,
  private val containerName: String?
) : WordRequestInfo {

  override fun getWord(): String = word

  override fun getSearchScope(): SearchScope = searchScope

  override fun isCaseSensitive(): Boolean = caseSensitive

  override fun getSearchContext(): Short = searchContext

  override fun getContainerName(): String? = containerName
}
