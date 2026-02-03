// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.experimental.meetNewUi

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.Row
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface MeetNewUiCustomization {
  fun addButtons(project: Project, row: Row)

  fun shouldCreateToolWindow(): Boolean = false

  fun showToolWindowOnStartup(): Boolean

  companion object {
    private val EP_NAME = ExtensionPointName<MeetNewUiCustomization>("com.intellij.meetNewUiCustomization")

    fun firstOrNull(): MeetNewUiCustomization? = EP_NAME.findFirstSafe { true }
  }
}