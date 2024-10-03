// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.ex.PrioritizedDocumentListener;
import org.jetbrains.annotations.ApiStatus;

/**
 * Document listeners are sorted according {@link PrioritizedDocumentListener#getPriority()}.
 * (the smaller the priority value the sooner the listener will be called)
 * Some standard priorities are listed here.
 */
@ApiStatus.Internal
public final class EditorDocumentPriorities {

  /**
   * Assuming that range marker listeners work only with document offsets and don't perform document dimension mappings like
   * {@code 'logical position -> visual position'}, {@code 'offset -> logical position'} etc.
   */
  public static final int RANGE_MARKER = 40;

  public static final int FOLD_MODEL = 60;
  public static final int LOGICAL_POSITION_CACHE = 65;
  public static final int EDITOR_TEXT_LAYOUT_CACHE = 70;
  public static final int LEXER_EDITOR = 80;
  public static final int SOFT_WRAP_MODEL = 100;
  public static final int EDITOR_TEXT_WIDTH_CACHE = 110;
  public static final int CARET_MODEL = 120;
  public static final int INLAY_MODEL = 150;
  public static final int EDITOR_DOCUMENT_ADAPTER = 160;

  private EditorDocumentPriorities() {
  }
}
