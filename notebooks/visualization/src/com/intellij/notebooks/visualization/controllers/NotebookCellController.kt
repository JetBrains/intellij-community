// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.controllers

import com.intellij.openapi.Disposable

interface NotebookCellController : Disposable.Default {
  /**
   * As there are so many possible document editing operations that can destroy cell inlays by removing document range they attached to,
   * the only option we have to preserve consistency is to check inlays validity
   * and recreate them if needed.
   * This logic is supposed to be as simple as check `isValid` and `offset` attributes of inlays
   * so it should not introduce significant performance degradation.
   */
  fun checkAndRebuildInlays() {
  }
}