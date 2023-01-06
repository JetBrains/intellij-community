// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.util

import net.miginfocom.layout.CC
import net.miginfocom.layout.LC

fun CC.gap(left: Int = 0, right: Int = 0, top: Int = 0, bottom: Int = 0): CC {
  return gap("$left", "$right", "$top", "$bottom")
}

fun CC.emptyGap(): CC {
  return gap("0", "0", "0", "0")
}

fun LC.emptyBorders(): LC {
  return gridGap("0", "0").insets("0", "0", "0", "0")
}