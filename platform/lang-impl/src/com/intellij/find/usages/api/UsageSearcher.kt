// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.usages.api

import com.intellij.model.search.Searcher
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.OverrideOnly

/**
 * Convenience interface for searchers providing additional queries to find [Usage]s by [UsageSearchParameters].
 */
@ApiStatus.Experimental
@OverrideOnly
interface UsageSearcher : Searcher<UsageSearchParameters, Usage>
