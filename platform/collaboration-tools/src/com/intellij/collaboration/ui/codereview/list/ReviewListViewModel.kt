// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.list

/**
 * Represent a view model for review list component
 *
 * For now, it contains only necessary for common code functionality since review providers implement their own specific view models
 */
interface ReviewListViewModel {
  // May do just a check if everything currently loaded is still up-to-date, rather than actually load new data.
  fun refresh()

  // Must reload the entire list.
  fun reload() {
    refresh()
  }
}
