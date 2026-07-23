package com.intellij.ide.actions.searcheverywhere

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Deprecated("The old Search Everywhere API is being sunset.")
interface SemanticSearchEverywhereContributor {
  fun isElementSemantic(element: Any): Boolean
}