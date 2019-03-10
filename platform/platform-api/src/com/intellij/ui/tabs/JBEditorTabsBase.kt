// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs

import com.intellij.openapi.Disposable
import java.awt.Color
import java.util.function.Supplier

/**
 * @author yole
 */
interface JBEditorTabsBase : JBTabsEx {
  /**
   * Creates a tab without firing any SelectionChanged events.
   *
   * @param info parameters of the tab
   * @param index zero-based index of the tab, or -1 to insert at the end
   * @param tabDisposable the disposable that will be disposed when the tab is closed
   * @return the same as the `info` parameter
   */
  fun addTabSilently(info: TabInfo, index: Int, tabDisposable: Disposable): TabInfo

  @Deprecated("Used only by the old tabs implementation")
  fun setEmptySpaceColorCallback(callback: Supplier<Color>);
}