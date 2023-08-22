// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.navigation

import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal

@Experimental
interface NavigationOptions {

  /**
   * Sets whether to request the focus.
   *
   * Default: `true`.
   */
  fun requestFocus(value: Boolean): NavigationOptions

  /**
   * If the navigation leads to a file, which is already open in some editor,
   * the editor will be focused, but the caret position will remain unchanged,
   * if the caret position is within text range of requested PsiElement.
   *
   * For example, when requesting navigation to PsiElement, which corresponds to class `C`:
   * ```
   * <caret>package com.foo.bar;
   * class C {  }
   * ```
   * the caret will be placed here:
   * ```
   * package com.foo.bar;
   * class <caret>C {  }
   * ```
   * But if the caret was already inside the [element range][com.intellij.platform.backend.navigation.impl.SourceNavigationRequest.elementRangeMarker],
   * it will remain unchanged:
   * ```
   * package com.foo.bar;
   * class C { <caret> }
   * ```
   *
   * Default: `false`.
   */
  fun preserveCaret(value: Boolean): NavigationOptions

  companion object {

    @JvmStatic
    fun defaultOptions(): NavigationOptions = defaultOptions

    private val defaultOptions = Impl(
      requestFocus = true,
      preserveCaret = false,
    )
  }

  @Internal
  data class Impl internal constructor(
    val requestFocus: Boolean,
    val preserveCaret: Boolean,
  ) : NavigationOptions {
    override fun requestFocus(value: Boolean): NavigationOptions = copy(requestFocus = value)
    override fun preserveCaret(value: Boolean): NavigationOptions = copy(preserveCaret = value)
  }
}
