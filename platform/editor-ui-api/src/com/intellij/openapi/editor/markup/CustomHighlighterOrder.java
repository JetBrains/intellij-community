// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.markup;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
public enum CustomHighlighterOrder {
  BEFORE_BACKGROUND,
  /** Paint over the background (defined by common highlighters) and selection, but before the text */
  AFTER_BACKGROUND,
  AFTER_TEXT
}
