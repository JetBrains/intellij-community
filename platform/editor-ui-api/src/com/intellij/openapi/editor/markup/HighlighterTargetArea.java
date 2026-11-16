// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.markup;

public enum HighlighterTargetArea {
  /**
   * The highlighter covers the text from its start offset to its end offset.
   * A background that covers a line break fills rest of the visual line.
   */
  EXACT_RANGE,

  /**
   * The highlighter covers each line that its range touches, from the line start to the line break included.
   * The background fills the whole visual line.
   */
  LINES_IN_RANGE
}
