// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.util

import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import org.jetbrains.annotations.ApiStatus
import java.awt.Insets

@ApiStatus.Internal
fun CC.gap(left: Int = 0, right: Int = 0, top: Int = 0, bottom: Int = 0): CC {
  return gap("$left", "$right", "$top", "$bottom")
}

@ApiStatus.Internal
fun CC.gap(insets: Insets): CC {
  return gap(left = insets.left, right = insets.right, top = insets.top, bottom = insets.bottom)
}

@ApiStatus.Internal
fun LC.emptyBorders(): LC {
  return gridGap("0", "0").insets("0", "0", "0", "0")
}