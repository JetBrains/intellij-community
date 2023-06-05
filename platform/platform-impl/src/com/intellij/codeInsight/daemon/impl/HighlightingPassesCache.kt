// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.openapi.vfs.VirtualFile

interface HighlightingPassesCache {
  fun loadPasses(files: List<VirtualFile>)
}