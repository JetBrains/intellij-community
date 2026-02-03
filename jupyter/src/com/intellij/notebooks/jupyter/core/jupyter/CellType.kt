// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.jupyter.core.jupyter

enum class CellType(val cellHeader: String) {
  CODE("#%%"),
  MARKDOWN("#%% md"),
  RAW("#%% raw")
}