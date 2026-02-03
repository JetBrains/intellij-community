package com.intellij.ide.actions.searcheverywhere

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface SemanticSearchEverywhereContributor {
  fun isElementSemantic(element: Any): Boolean
}