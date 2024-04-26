// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui.icon

import com.intellij.util.concurrency.annotations.RequiresEdt
import javax.swing.Icon

/**
 * @param T - icon key type
 */
fun interface IconsProvider<T> {

  /**
   * @param key - icon key
   * @param iconSize - required icon size in pixels (unscaled)
   */
  @RequiresEdt
  fun getIcon(key: T?, iconSize: Int): Icon
}
