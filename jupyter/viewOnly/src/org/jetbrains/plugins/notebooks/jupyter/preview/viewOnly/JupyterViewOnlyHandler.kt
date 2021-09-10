// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.notebooks.jupyter.preview.viewOnly

import org.jetbrains.plugins.notebooks.jupyter.preview.JupyterCefHttpHandlerBase

class JupyterViewOnlyHandler : JupyterCefHttpHandlerBase(absolutePathFiles = listOf(
  "/ipywidgets.html",
  "/ipywidgets.css",
  "/ipywidgets.js")) {
  override val appName: String = "jupyter-view-only"
}
