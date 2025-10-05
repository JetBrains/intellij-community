// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
abstract class SplitSearchAdapter : SplitSearchListener {
  override fun elementsAdded(uuidToElement: Map<String, Any>) {
  }

  override fun elementsRemoved(count: Int) {
  }

  override fun searchFinished(count: Int) {
  }

  override fun searchStarted(pattern: String, tabId: String) {
  }
}