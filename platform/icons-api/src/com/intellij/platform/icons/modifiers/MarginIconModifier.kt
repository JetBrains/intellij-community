// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.modifiers

import com.intellij.platform.icons.IconManager
import com.intellij.platform.icons.design.IconUnit

fun IconModifier.margin(
  left: IconUnit = IconUnit.Zero,
  top: IconUnit = IconUnit.Zero,
  right: IconUnit = IconUnit.Zero,
  bottom: IconUnit = IconUnit.Zero,
): IconModifier =
    this then IconManager.modifiers().margin(left, top, right, bottom)

fun IconModifier.margin(all: IconUnit): IconModifier = margin(all, all, all, all)

fun IconModifier.margin(vertical: IconUnit, horizontal: IconUnit): IconModifier =
    margin(horizontal, vertical, horizontal, vertical)
