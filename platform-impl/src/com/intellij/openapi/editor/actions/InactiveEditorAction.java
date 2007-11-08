/*
 * @author max
 */
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import org.jetbrains.annotations.Nullable;

public class InactiveEditorAction extends EditorAction {
  protected InactiveEditorAction(EditorActionHandler defaultHandler) {
    super(defaultHandler);
  }

  @Nullable
  protected Editor getEditor(final DataContext dataContext) {
    return DataKeys.EDITOR_EVEN_IF_INACTIVE.getData(dataContext);
  }
}