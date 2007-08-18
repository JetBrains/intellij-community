
package com.intellij.ide.actions;

import com.intellij.ide.CopyProvider;
import com.intellij.openapi.actionSystem.*;

public class CopyAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    CopyProvider provider = (CopyProvider)dataContext.getData(DataConstants.COPY_PROVIDER);
    if (provider == null) return;
    provider.performCopy(dataContext);
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    CopyProvider provider = (CopyProvider)dataContext.getData(DataConstants.COPY_PROVIDER);
    presentation.setEnabled(provider != null && provider.isCopyEnabled(dataContext));
  }
}
