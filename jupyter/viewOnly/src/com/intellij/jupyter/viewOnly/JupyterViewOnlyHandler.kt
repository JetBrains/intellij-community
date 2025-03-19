// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jupyter.viewOnly

import com.intellij.notebooks.jupyter.core.jupyter.preview.JupyterCefHttpHandlerBase

class JupyterViewOnlyHandler : JupyterCefHttpHandlerBase(
  absolutePathFiles = hashSetOf("/ipywidgets.html", "/ipywidgets.css", "/ipywidgets.js")
) {
  override val appName: String = "jupyter-view-only"
}
