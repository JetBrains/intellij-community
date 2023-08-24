// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.ide.ui

import com.intellij.model.Pointer
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import org.jetbrains.annotations.ApiStatus.Experimental
import javax.swing.JComponent

@Experimental
interface DocumentationComponent {
  fun getComponent(): JComponent

  fun resetBrowser()

  fun resetBrowser(targetPointer: Pointer<out DocumentationTarget>,
                   targetPresentation: TargetPresentation)
}