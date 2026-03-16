// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui

import com.intellij.ui.icons.UpdatableIcon
import com.intellij.util.concurrency.annotations.RequiresEdt
import javax.swing.Icon

interface DeferredIcon : UpdatableIcon {
  val baseIcon: Icon

  @get:RequiresEdt
  val isDone: Boolean

  fun evaluate(): Icon
}
