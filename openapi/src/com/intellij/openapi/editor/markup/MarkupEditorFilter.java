/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor.markup;

import com.intellij.openapi.editor.Editor;

/**
 * @author max
 */
public interface MarkupEditorFilter {
  MarkupEditorFilter EMPTY = new MarkupEditorFilter() {
    public boolean avaliableIn(Editor editor) {
      return true;
    }
  };

  boolean avaliableIn(Editor editor);
}
