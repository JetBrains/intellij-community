// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

interface SearchEverywhereExtendedInfoProvider {
  /**
   * There is a footer in the search everywhere popup that shows additional information for a selected item.
   * E.g., it renders description and "Assign/Change" shortcut for a selected action.
   * `ExtendedInfo` is responsible for building this component.
   */
  fun createExtendedInfo(): ExtendedInfo? = null
}