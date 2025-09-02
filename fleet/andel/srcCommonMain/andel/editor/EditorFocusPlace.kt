// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.editor

sealed class EditorFocusPlace {
  data class Interline(val interline: andel.lines.Interline) : EditorFocusPlace()
  data class Fold(val fold: andel.lines.Fold) : EditorFocusPlace()
  data object EditorText: EditorFocusPlace()
}