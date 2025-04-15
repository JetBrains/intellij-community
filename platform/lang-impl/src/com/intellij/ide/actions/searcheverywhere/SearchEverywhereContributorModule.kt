// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.Processor
import org.jetbrains.annotations.ApiStatus
import javax.swing.ListCellRenderer

// Rider SE needs (a) deeper (b) more dynamic integrations with the search everywhere process
// due to
// (a) Rider essentially having its own Global Navigation implementation on its backend with its own rules.
// (b) Rider needing to -compositionally- integrate both with the "vanilla" and the "semantic" search.
@ApiStatus.Internal
interface SearchEverywhereContributorModule {

  // Extended info should be handled differently. Prefer composition over tag interfaces and inheritance of ExtendedInfoProvider
  fun mixinExtendedInfo(baseExtendedInfo: ExtendedInfo): ExtendedInfo

  fun processSelectedItem(item: Any, modifiers: Int, searchTest: String): Boolean?

  fun getOverridingElementRenderer(parent: Disposable): ListCellRenderer<in Any?>

  fun perProductFetchWeightedElements(pattern: String, progressIndicator: ProgressIndicator, consumer: Processor<in FoundItemDescriptor<Any>>)

  fun currentSearchEverywhereToggledActionChanged(newAction: SearchEverywhereToggleAction)
}