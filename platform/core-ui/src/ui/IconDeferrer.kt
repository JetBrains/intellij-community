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
   * Moreover, the returned icons will be considered equal if they have equal `param` values.
   * So even if you clear the cache manually, you may still have some obsolete value stuck somewhere
   * because some setter ignored the new value as equal to the old one.
   * So make sure you use a new key after you clear the cache.
   * For example, include a sequential counter and increment it every time you clear the cache.
   */
  abstract fun <T : Any> defer(base: Icon?, param: T, evaluator: (T) -> Icon?): Icon

  /**
   * @param param Unique key that WILL BE USED to cache the icon instance.
   * Prefer passing unique objects over [String] or [Integer] to avoid accidental clashes with another module.
   */
  abstract fun <T : Any> deferAsync(base: Icon?, param: T, evaluator: suspend (T) -> Icon?): Icon

  abstract fun clearCache()
}