// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal

import com.jediterm.core.Color
import com.jediterm.terminal.emulator.ColorPalette

abstract class TerminalColorPalette : ColorPalette() {
  abstract val defaultForeground: Color
  abstract val defaultBackground: Color
}