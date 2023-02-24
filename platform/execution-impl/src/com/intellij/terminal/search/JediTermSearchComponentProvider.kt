// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.search

import com.intellij.terminal.JBTerminalWidget
import com.jediterm.terminal.ui.JediTermSearchComponent

interface JediTermSearchComponentProvider {
  fun createSearchComponent(jediTermWidget: JBTerminalWidget): JediTermSearchComponent
}
