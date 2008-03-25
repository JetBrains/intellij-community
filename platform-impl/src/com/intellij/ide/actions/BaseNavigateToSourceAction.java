package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.pom.Navigatable;
import com.intellij.util.OpenSourceUtil;

public abstract class BaseNavigateToSourceAction extends AnAction {
  private final boolean myFocusEditor;

  protected BaseNavigateToSourceAction(boolean focusEditor) {
    myFocusEditor = focusEditor;
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    OpenSourceUtil.openSourcesFrom(dataContext, myFocusEditor);
  }


  public void update(AnActionEvent event){
    DataContext dataContext = event.getDataContext();
    event.getPresentation().setEnabled(isEnabled(dataContext));
  }

  private static boolean isEnabled(final DataContext dataContext) {
    Navigatable[] navigatables = PlatformDataKeys.NAVIGATABLE_ARRAY.getData(dataContext);
    if (navigatables != null) {
      for (Navigatable navigatable : navigatables) {
        if (navigatable.canNavigate()) return true;
      }
    }
    return false;    
  }

}
