/*
 * Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.components

import org.jetbrains.plugins.notebooks.visualization.r.inlays.InlayOutput

abstract class NotebookInlayMultiOutput : NotebookInlayState() {
  abstract fun onOutputs(inlayOutputs: List<InlayOutput>)
}