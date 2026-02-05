// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.legacyIconSupport

import org.jetbrains.icons.Icon
import org.jetbrains.icons.design.IconDesigner
import org.jetbrains.icons.modifiers.IconModifier

interface SwingIconManager {
  fun toSwingIcon(icon: Icon): javax.swing.Icon
}