
package com.intellij.ide.actions;

import com.intellij.ide.CutProvider;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;

public class CutAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    CutProvider provider = (CutProvider)dataContext.getData(DataConstantsEx.CUT_PROVIDER);
    if (provider == null) return;
    provider.performCut(dataContext);
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    CutProvider provider = (CutProvider)dataContext.getData(DataConstantsEx.CUT_PROVIDER);
    presentation.setEnabled(provider != null && provider.isCutEnabled(dataContext));
  }
}
