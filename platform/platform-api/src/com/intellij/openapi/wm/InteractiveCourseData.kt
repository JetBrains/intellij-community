// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm

import org.jetbrains.annotations.Nls
import javax.swing.Action
import javax.swing.Icon
import javax.swing.JComponent

interface InteractiveCourseData {

  @Nls
  fun getName(): String

  @Nls
  fun getDescription(): String

  fun getIcon(): Icon

  @Nls
  fun getActionButtonName(): String

  fun getAction(): Action

  fun newContentMarker(): JComponent? = null

  // it's a method to implement temporary design for 2022.3, it will be removed in 2023.1
  fun isEduTools(): Boolean = false
}