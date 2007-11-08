package com.intellij.openapi.editor.markup;

import com.intellij.openapi.diff.DiffManager;
import com.intellij.openapi.editor.Editor;

/**
 * @author max
 */
public class MarkupEditorFilterFactory {
  private static final MarkupEditorFilter IS_DIFF_FILTER = DiffManager.getInstance().getDiffEditorFilter();
  private static final MarkupEditorFilter NOT_DIFF_FILTER = createNotFilter(IS_DIFF_FILTER);

  public static MarkupEditorFilter createNotFilter(final MarkupEditorFilter filter) {
    return new MarkupEditorFilter() {
      public boolean avaliableIn(Editor editor) {
        return !filter.avaliableIn(editor);
      }
    };
  }

  public static MarkupEditorFilter createIsDiffFilter() {
    return IS_DIFF_FILTER;
  }

  public static MarkupEditorFilter createIsNotDiffFilter() {
    return NOT_DIFF_FILTER;
  }
}
