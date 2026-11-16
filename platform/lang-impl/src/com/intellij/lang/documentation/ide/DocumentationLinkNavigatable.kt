// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.ide

import com.intellij.openapi.editor.Editor
import com.intellij.pom.Navigatable
import org.jetbrains.annotations.ApiStatus
import java.awt.Point

@ApiStatus.Internal
interface DocumentationLinkNavigatable : Navigatable {
  /** Places a navigation popup at [popupPosition] in the [editor] content component. */
  fun navigate(editor: Editor, popupPosition: Point)
}
