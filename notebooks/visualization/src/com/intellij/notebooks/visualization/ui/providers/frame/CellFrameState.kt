// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui.providers.frame

import com.intellij.ui.JBColor
import java.awt.Color

data class CellFrameState(val isVisible: Boolean = false, val color: Color = JBColor.background())