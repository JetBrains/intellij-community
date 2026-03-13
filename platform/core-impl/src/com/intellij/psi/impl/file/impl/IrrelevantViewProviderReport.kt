// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.openapi.vfs.VirtualFile

internal class IrrelevantViewProviderReport(
  // List of all files stored in the cache that have at least one relevant view provider
  val filesHavingRelevantViewProviders: Set<VirtualFile>,

  // Map of files that have irrelevant view providers.
  // Stores the first irrelevant view provider for each file
  val irrelevantViewProviders: Map<VirtualFile, FileViewProviderCache.Entry>,
)

