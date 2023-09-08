// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import javax.swing.Icon

abstract class IconDeferrer {
  companion object {
    @JvmStatic
    fun getInstance(): IconDeferrer = ApplicationManager.getApplication().service<IconDeferrer>()
  }

  /**
   * @param param Unique key that WILL BE USED to cache the icon instance.
   * Prefer passing unique objects over [String] or [Integer] to avoid accidental clashes with another module.
   */
  abstract fun <T : Any> defer(base: Icon?, param: T, evaluator: (T) -> Icon?): Icon

  abstract fun clearCache()
}