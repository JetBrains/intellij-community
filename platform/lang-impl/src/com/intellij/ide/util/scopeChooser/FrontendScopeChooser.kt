// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.scopeChooser

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.JComboBox
import javax.swing.JComponent

@ApiStatus.Internal
interface FrontendScopeChooser {
  val component: JComponent

  val comboBox: JComboBox<*>

  val selectedScopeName: @Nls String?
  val selectedScopeId: String?

  fun cancelActivities()

  suspend fun awaitScopeSelection()
}
