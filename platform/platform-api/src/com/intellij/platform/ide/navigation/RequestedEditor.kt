// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.navigation

import com.intellij.openapi.editor.Editor
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * A way to specify editor to use exactly.
 * @see NavigationOptions.requestedEditor
 */
@Internal
sealed interface RequestedEditor {
  /**
   * No preference is set, i.e., an editor published by the UI context of the navigation, if any, is reused.
   */
  object Unspecified : RequestedEditor

  /**
   * No editor is reused, even when the UI context of the navigation publishes one.
   * The target editor is chosen by the platform.
   *
   * For all consumers, this option is treated as `null` editor.
   */
  object None : RequestedEditor

  /**
   * [editor] is reused, provided it is not disposed and displays the target file; otherwise the platform chooses the editor.
   */
  class Specific(@JvmField val editor: Editor) : RequestedEditor
}
