package com.intellij.codeInsight.hint;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.Project;

public class EscapeHandler extends EditorActionHandler {
  private final EditorActionHandler myOriginalHandler;

  public EscapeHandler(EditorActionHandler originalHandler) {
    myOriginalHandler = originalHandler;
  }

  public void execute(Editor editor, DataContext dataContext) {
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null || !HintManagerImpl.getInstanceImpl().hideHints(HintManagerImpl.HIDE_BY_ESCAPE | HintManagerImpl.HIDE_BY_ANY_KEY, true, false)) {
      myOriginalHandler.execute(editor, dataContext);
    }
  }

  public boolean isEnabled(Editor editor, DataContext dataContext) {
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);

    if (project != null) {
      HintManagerImpl hintManager = HintManagerImpl.getInstanceImpl();
      return hintManager.isEscapeHandlerEnabled();
    }

    return myOriginalHandler.isEnabled(editor, dataContext);
  }
}
