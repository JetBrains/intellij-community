// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm

import com.intellij.openapi.extensions.ExtensionPointName
import javax.swing.JComponent

interface InteractiveCourseFactory {

  companion object {
    val INTERACTIVE_COURSE_FACTORY_EP: ExtensionPointName<InteractiveCourseFactory> = ExtensionPointName("com.intellij.interactiveCourseFactory")
  }

  val isActive: Boolean

  val isEnabled: Boolean

  val disabledText: String
  fun getInteractiveCourseComponent(): JComponent

  fun getCourseData(): InteractiveCourseData
}
