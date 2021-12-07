// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.codeVision.settings

import javax.swing.JComponent

interface CodeVisionGroupSettingModel {

  val name: String
  var isEnabled: Boolean

  val description: String?
  val component: JComponent?

  fun isModified(): Boolean
  fun apply()
  fun reset()
}