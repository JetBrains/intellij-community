// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf

import com.intellij.ide.ui.UITheme
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus
import javax.swing.UIDefaults

@ApiStatus.Internal
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface UIThemeLookAndFeelInfo  {
  val id: String
    get() = theme.id
  @get:NlsSafe
  val name: String
    get() = theme.name!!
  val isDark: Boolean
    get() = theme.isDark

  val editorSchemeName: String?
    get() = theme.editorSchemeName

  val theme: UITheme

  val isInitialized: Boolean

  fun installTheme(defaults: UIDefaults, lockEditorScheme: Boolean)

  fun dispose()
}
