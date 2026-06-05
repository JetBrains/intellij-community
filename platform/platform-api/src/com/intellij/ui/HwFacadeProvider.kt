// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
interface HwFacadeProvider {
  fun isAvailable(): Boolean

  fun create(target: JComponent): HwFacadeHelper
}
