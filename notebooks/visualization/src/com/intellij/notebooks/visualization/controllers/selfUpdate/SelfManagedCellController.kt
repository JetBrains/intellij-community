// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.controllers.selfUpdate

import com.intellij.notebooks.visualization.controllers.NotebookCellController

/**
 * Controller which does not rely on external recreate and allow safe updater UI
 */
interface SelfManagedCellController : NotebookCellController