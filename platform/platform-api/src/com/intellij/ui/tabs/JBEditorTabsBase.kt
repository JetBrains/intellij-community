// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs

import java.awt.Color
import java.util.function.Supplier


interface JBEditorTabsBase : JBTabs {
  @Deprecated("Used only by the old tabs implementation")
  fun setEmptySpaceColorCallback(callback: Supplier<out Color>)
}