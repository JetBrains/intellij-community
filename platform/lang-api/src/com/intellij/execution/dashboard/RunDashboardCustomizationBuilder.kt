// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.dashboard

import com.intellij.ui.SimpleTextAttributes
import javax.swing.Icon

interface RunDashboardCustomizationBuilder {
  fun addText(text: String, attributes: SimpleTextAttributes): RunDashboardCustomizationBuilder
  fun setClearText(): RunDashboardCustomizationBuilder
  fun setIcon(icon: Icon): RunDashboardCustomizationBuilder
  fun addLink(value: String, callback: Runnable): RunDashboardCustomizationBuilder
}