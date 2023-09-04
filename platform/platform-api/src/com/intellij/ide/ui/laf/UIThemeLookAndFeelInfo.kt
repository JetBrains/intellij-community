// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf

import com.intellij.ide.ui.UITheme
import org.jetbrains.annotations.ApiStatus
import javax.swing.UIManager.LookAndFeelInfo

@ApiStatus.Internal
@ApiStatus.Experimental
abstract class UIThemeLookAndFeelInfo protected constructor(val theme: UITheme)
  : LookAndFeelInfo
    (
      theme.name,
      // todo one one should be used in the future
      if (theme.isDark) "com.intellij.ide.ui.laf.darcula.DarculaLaf" else "com.intellij.ide.ui.laf.IntelliJLaf",
    )
