// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.api

import com.intellij.model.search.Searcher

/**
 * Convenience interface for searchers providing additional queries to find [RenameUsage]s by [Symbol][com.intellij.model.Symbol].
 */
interface RenameUsageSearcher : Searcher<RenameUsageSearchParameters, RenameUsage>
