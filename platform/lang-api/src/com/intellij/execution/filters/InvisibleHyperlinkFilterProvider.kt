// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.filters

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.ApiStatus

/**
 * Provides [Filter]s that find invisible hyperlinks in the hovered output line.
 *
 * An invisible hyperlink looks like plain text until the mouse pointer is over it,
 * see [Filter.ResultItem.isInvisibleLink]. Unlike the filters of [ConsoleFilterProvider],
 * these filters run only for the line under the pointer, not for every output line,
 * so they stay off the output hot path. Consumers that do not compute hyperlinks on
 * hover ignore this extension point.
 *
 * The [GlobalSearchScope] identifies the consumer, as in
 * [ConsoleFilterProviderEx.getDefaultFilters].
 */
@ApiStatus.Internal
interface InvisibleHyperlinkFilterProvider {
  /**
   * Returns the filters for the consumer identified by [scope],
   * or an empty list if the provider has nothing to contribute.
   */
  fun getFilters(project: Project, scope: GlobalSearchScope): List<Filter>

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<InvisibleHyperlinkFilterProvider> =
      ExtensionPointName("com.intellij.invisibleHyperlinkFilterProvider")
  }
}
