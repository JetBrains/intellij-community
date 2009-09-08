
package com.intellij.ide.actions;

import com.intellij.ide.CopyProvider;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;

public class CopyAction extends AnAction implements DumbAware {
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
    if (event.getPlace().equals(ActionPlaces.EDITOR_POPUP) && provider != null) {
      presentation.setVisible(provider.isCopyVisible(dataContext));
    }
    else {
      presentation.setVisible(true);
    }
  }
}
