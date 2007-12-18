package com.intellij.codeInsight.folding.impl.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.FoldingModelEx;

public class FoldingGroup extends DefaultActionGroup {
  public FoldingGroup() {
    super();
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();

    Editor editor = DataKeys.EDITOR.getData(dataContext);
    if (editor == null){
      presentation.setVisible(false);
      return;
    }

    FoldingModelEx foldingModel = (FoldingModelEx)editor.getFoldingModel();
    presentation.setVisible(foldingModel.isFoldingEnabled());
  }
}