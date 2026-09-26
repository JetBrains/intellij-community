// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.SegmentedButton.ItemPresentation
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.Icon

@ApiStatus.Internal
interface KotlinUiDslService {

  companion object {
    fun getInstance(): KotlinUiDslService {
      return ApplicationManager.getApplication().getService(KotlinUiDslService::class.java)
             ?: throw IllegalStateException("No KotlinUiDslService service found")
    }
  }

  fun panel(init: Panel.() -> Unit): DialogPanel

  fun createPresentation(text: @Nls String?, toolTipText: @Nls String?, icon: Icon?, enabled: Boolean): ItemPresentation

}
