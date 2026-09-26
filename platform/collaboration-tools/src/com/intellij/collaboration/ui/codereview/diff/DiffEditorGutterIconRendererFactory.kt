// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui.codereview.diff

@Deprecated("Deprecated with the move to ViewModel-based approach")
interface DiffEditorGutterIconRendererFactory {
  fun createCommentRenderer(line: Int): AddCommentGutterIconRenderer
}
