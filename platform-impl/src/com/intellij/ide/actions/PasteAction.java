package com.intellij.ide.actions;

import com.intellij.ide.PasteProvider;
import com.intellij.openapi.actionSystem.*;

public class PasteAction extends AnAction {

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();

    PasteProvider provider = (PasteProvider)dataContext.getData(DataConstants.PASTE_PROVIDER);
    presentation.setEnabled(provider != null && provider.isPastePossible(dataContext));
  }
  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    PasteProvider provider = (PasteProvider)dataContext.getData(DataConstants.PASTE_PROVIDER);
    if (provider == null || !provider.isPasteEnabled(dataContext)) return;
    provider.performPaste(dataContext);
  }
}