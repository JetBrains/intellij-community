// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.ui.visualization

interface NotebookEditorAppearanceFlags {
  fun shouldShowCellLineNumbers(): Boolean
  fun shouldShowExecutionCounts(): Boolean
  fun shouldShowOutExecutionCounts(): Boolean
  fun shouldShowRunButtonInGutter(): Boolean
}