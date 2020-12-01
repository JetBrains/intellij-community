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

  fun getExpandContent(): JComponent

}