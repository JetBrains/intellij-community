package com.intellij.codeInsight.template.impl.editorActions;

import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateSettings;

public class EnterHandler extends EditorWriteActionHandler {
  private EditorActionHandler myOriginalHandler;

  public EnterHandler(EditorActionHandler originalHandler) {
    myOriginalHandler = originalHandler;
  }

  public boolean isEnabled(Editor editor, DataContext dataContext) {
    return myOriginalHandler.isEnabled(editor, dataContext);
  }

  public void executeWriteAction(Editor editor, DataContext dataContext) {
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);

    if (project == null) {
      myOriginalHandler.execute(editor, dataContext);
      return;
    }

    TemplateManagerImpl templateManager = (TemplateManagerImpl) TemplateManagerImpl.getInstance(project);

    if (!templateManager.startTemplate(templateManager, editor, TemplateSettings.ENTER_CHAR)) {
      myOriginalHandler.execute(editor, dataContext);
    }
  }
}
