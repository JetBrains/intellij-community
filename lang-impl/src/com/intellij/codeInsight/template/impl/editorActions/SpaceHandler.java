package com.intellij.codeInsight.template.impl.editorActions;

import com.intellij.openapi.editor.actionSystem.TypedActionHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateSettings;

public class SpaceHandler implements TypedActionHandler {
  private TypedActionHandler myOriginalHandler;

  public SpaceHandler(TypedActionHandler originalHandler) {
    myOriginalHandler = originalHandler;
  }

  public void execute(Editor editor, char charTyped, DataContext dataContext) {
    if (charTyped != ' ') {
      myOriginalHandler.execute(editor, charTyped, dataContext);
      return;
    }

    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      myOriginalHandler.execute(editor, charTyped, dataContext);
      return;
    }

    TemplateManagerImpl templateManager = (TemplateManagerImpl) TemplateManagerImpl.getInstance(project);
    if (!templateManager.startTemplate(templateManager, editor, TemplateSettings.SPACE_CHAR)) {
      myOriginalHandler.execute(editor, charTyped, dataContext);
    }
  }
}
