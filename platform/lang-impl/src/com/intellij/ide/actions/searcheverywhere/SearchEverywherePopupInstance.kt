// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.Future
import javax.swing.text.Document

@ApiStatus.Experimental
interface SearchEverywherePopupInstance {
  fun getSearchText(): String?
  fun setSearchText(searchText: String?)
  fun getSearchFieldDocument(): Document
  fun closePopup()
  fun addSearchListener(listener: SearchListener)
  fun addSplitSearchListener(listener: SplitSearchListener)

  @ApiStatus.Internal
  fun selectFirstItem()

  @ApiStatus.Internal
  fun changeScope(processor: (scope: ScopeDescriptor, scopeList: List<ScopeDescriptor>) -> ScopeDescriptor?)

  @ApiStatus.Internal
  @TestOnly
  fun findElementsForPattern(pattern: String): Future<List<Any>>
}