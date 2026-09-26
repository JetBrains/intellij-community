// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.fuzzyMatching

import com.intellij.openapi.Disposable
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface GotoFileFuzzyMatcher : Disposable {
  fun match(file: VirtualFile): FuzzyMatchResult
  fun match(fileName: String): FuzzyMatchResult
  fun matchWithPath(file: VirtualFile): FuzzyMatchResult
}
