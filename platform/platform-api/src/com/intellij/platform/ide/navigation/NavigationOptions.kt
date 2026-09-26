// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.navigation

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal

@ApiStatus.NonExtendable
interface NavigationOptions {
  /**
   * Sets whether to request the focus.
   *
   * Default: `true`.
   */
  fun requestFocus(value: Boolean): NavigationOptions

  /**
   * If the navigation leads to a file, which is already open in some editor, the editor will be focused. But the caret position will remain
   * unchanged when the caret position is within text range of the requested PsiElement.
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

  fun openInRightSplit(value: Boolean): NavigationOptions

  /**
   * States which editor the navigation is to reuse, if any.
   * Wins over context [com.intellij.openapi.fileEditor.OpenFileDescriptor.NAVIGATE_IN_EDITOR].
   *
   * Default: [RequestedEditor.Unspecified].
   */
  @Internal
  fun requestedEditor(value: RequestedEditor): NavigationOptions

  /**
   * Defines where the caret is placed once the navigation reached its target.
   *
   * Reliably applies to structured source navigation, i.e. when the target is known upfront as a file and an offset in it, see
   * [NavigationRequest.sourceNavigationRequest][com.intellij.platform.backend.navigation.NavigationRequest.sourceNavigationRequest].
   * A [com.intellij.pom.Navigatable] which instead navigates by running its own [com.intellij.pom.Navigatable.navigate] code reports
   * no target: the placement is then applied after that navigation and only while the target it was going to may still be identified,
   * and it is ignored altogether for a navigatable which does not tell its target.
   *
   * Default: [CaretPlacement.TARGET_OFFSET].
   */
  @Internal
  fun caretPlacement(placement: CaretPlacement): NavigationOptions

  @Internal
  fun sourceNavigationOnly(value: Boolean): NavigationOptions

  /**
   * Sets whether to force the focus regardless of other conditions.
   * Forcing the focus implies requesting it, so `forceFocus(true)` also enables [requestFocus].
   *
   * Default: `false`.
   */
  @Internal
  fun forceFocus(value: Boolean): NavigationOptions

  /**
   * Identifies if the navigation should be recorded in the history.
   * Captures a place where navigation started, commits as a back-history entry after the navigation completes,
   * omitting records with no changes.
   *
   * Should be set to `false` for autoscroll, preview, other programmatic navigation.
   */
  @Internal
  fun recordAsBackHistory(value: Boolean): NavigationOptions

  companion object {
    @JvmStatic
    fun defaultOptions(): NavigationOptions = defaultOptions

    @JvmStatic
    fun requestFocus(): NavigationOptions {
      return defaultOptions().requestFocus(true)
    }

    @Internal
    val KEY: DataKey<NavigationOptions> = DataKey.create("navigation.options")

    /**
     * @return stored variant, or [requestFocus] by default
     */
    @Internal
    @JvmStatic
    fun fromContext(dataContext: DataContext): NavigationOptions = KEY.getData(dataContext) ?: requestFocus()

    private val defaultOptions = Impl(
      requestFocus = true,
      preserveCaret = false,
      openInRightSplit = false,
      sourceNavigationOnly = false,
      forceFocus = false,
      recordAsBackHistory = true,
      requestedEditor = RequestedEditor.Unspecified,
      caretPlacement = CaretPlacement.TARGET_OFFSET,
    )
  }

  @Internal
  @ConsistentCopyVisibility
  data class Impl internal constructor(
    val requestFocus: Boolean,
    val preserveCaret: Boolean,
    // some UI uses single-click navigation instead of double-click; in this case we want only source navigation
    // but not opening library settings (https://youtrack.jetbrains.com/issue/IJPL-157790)
    @Experimental @JvmField val sourceNavigationOnly: Boolean,
    @Experimental @JvmField val openInRightSplit: Boolean,
    @Experimental @JvmField val forceFocus: Boolean,
    @Experimental @JvmField val recordAsBackHistory: Boolean,
    @Experimental @JvmField val requestedEditor: RequestedEditor,
    @Experimental @JvmField val caretPlacement: CaretPlacement,
  ) : NavigationOptions {
    override fun requestFocus(value: Boolean): NavigationOptions = copy(requestFocus = value)

    override fun preserveCaret(value: Boolean): NavigationOptions = copy(preserveCaret = value)

    override fun openInRightSplit(value: Boolean): NavigationOptions = copy(openInRightSplit = value)

    override fun sourceNavigationOnly(value: Boolean): NavigationOptions = copy(sourceNavigationOnly = value)

    override fun forceFocus(value: Boolean): NavigationOptions = copy(forceFocus = value, requestFocus = if (value) true else requestFocus)

    override fun recordAsBackHistory(value: Boolean): NavigationOptions = copy(recordAsBackHistory = value)

    override fun requestedEditor(value: RequestedEditor): NavigationOptions = copy(requestedEditor = value)

    override fun caretPlacement(placement: CaretPlacement): NavigationOptions = copy(caretPlacement = placement)
  }
}
