// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.controllers.selfUpdate

import com.intellij.notebooks.visualization.controllers.NotebookCellController
import com.intellij.openapi.Disposable

/**
 * Controller which does not rely on external recreate and allow safe updater UI
 * All controllers in the future should implement this interface
 */
interface SelfManagedCellController : NotebookCellController, Disposable.Default {

  /**
   * Update internal state of controller
   */
  fun selfUpdate()
}