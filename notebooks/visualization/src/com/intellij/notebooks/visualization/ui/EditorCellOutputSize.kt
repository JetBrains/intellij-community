// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui

import java.awt.Dimension

data class EditorCellOutputSize(
  val size: Dimension? = null,
  val collapsed: Boolean = false,
  val maximized: Boolean = false,
  val resized: Boolean = false,
)