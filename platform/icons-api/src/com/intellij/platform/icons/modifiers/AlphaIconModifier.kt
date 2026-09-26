// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.modifiers

import com.intellij.platform.icons.IconManager

fun IconModifier.alpha(alpha: Float): IconModifier = this then IconManager.modifiers().alpha(alpha)
