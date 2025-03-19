// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.speedSearch

import org.jetbrains.annotations.ApiStatus.Internal
import javax.swing.JComponent

@Internal
interface SpeedSearchActivator {

  val isSupported: Boolean
  val isAvailable: Boolean
  val isActive: Boolean
  val textField: JComponent?

  fun activate()

}
