// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.pom.Navigatable
import com.intellij.pom.NavigatableWithText
import com.intellij.util.containers.map2Array
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class NavigatableInterceptor(private val baseNavigatable: Navigatable, val callback: (Navigatable, Boolean) -> Unit) : NavigatableWithText {
  override fun navigate(requestFocus: Boolean) {
    callback(baseNavigatable, requestFocus)
    baseNavigatable.navigate(requestFocus)
  }

  override fun canNavigate(): Boolean {
    return baseNavigatable.canNavigate()
  }

  override fun canNavigateToSource(): Boolean {
    return baseNavigatable.canNavigateToSource()
  }

  override fun getNavigateActionText(focusEditor: Boolean): String? {
    return (baseNavigatable as? NavigatableWithText)?.getNavigateActionText(focusEditor)
  }

  companion object {
    @JvmStatic
    fun wrap(navigatables: Array<Navigatable>, callback: (Navigatable, Boolean) -> Unit): Array<NavigatableInterceptor> {
      return navigatables.map2Array { NavigatableInterceptor(it, callback) }
    }
  }
}
