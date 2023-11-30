// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.ide.ui

import com.intellij.model.Pointer
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import javax.swing.JComponent

@Experimental
@Internal
interface DocumentationComponent {

  /**
   * Ready-to-use component, which should be added into UI hierarchy.
   * The actual component might change at any time.
   */
  fun getComponent(): JComponent

  /**
   * Requests the browser to display an empty content.
   */
  fun resetBrowser()

  /**
   * Requests the browser to display the documentation of [DocumentationTarget] referenced by [targetPointer].
   */
  fun resetBrowser(targetPointer: Pointer<out DocumentationTarget>, targetPresentation: TargetPresentation)

  /**
   * Emitted when component size is need to be updated, because content preferred size is changed.
   * For now the value is a [String] with human-readable description of the update reason, useful for debugging.
   * The actual value might change at any time.
   */
  val contentSizeUpdates: Flow<Any>
}
