// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui.providers.bounds

import java.util.*

fun interface JupyterBoundsChangeListener : EventListener {
  fun boundsChanged(): Unit?
}