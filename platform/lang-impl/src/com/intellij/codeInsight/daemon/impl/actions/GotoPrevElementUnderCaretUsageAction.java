// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.actions;

import org.jetbrains.annotations.ApiStatus;

/**
 * Action moves caret to the previous highlighted element under caret.
 *
 * Please note, it works only if option "Highlight usages of element at caret" turned on.
 * @see com.intellij.codeInsight.CodeInsightSettings#HIGHLIGHT_IDENTIFIER_UNDER_CARET highlight usages
 */
@ApiStatus.Internal
public final class GotoPrevElementUnderCaretUsageAction extends GotoElementUnderCaretUsageBase {
  public GotoPrevElementUnderCaretUsageAction() {
    super(Direction.BACKWARD);
  }
}
