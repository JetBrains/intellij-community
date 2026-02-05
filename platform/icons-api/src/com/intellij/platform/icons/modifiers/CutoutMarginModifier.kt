// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.modifiers

import com.intellij.platform.icons.IconManager
import com.intellij.platform.icons.design.IconUnit

fun IconModifier.cutoutMargin(size: IconUnit): IconModifier = this then IconManager.modifiers().cutoutMargin(size)
