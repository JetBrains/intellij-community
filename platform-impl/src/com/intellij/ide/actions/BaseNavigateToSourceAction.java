package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.pom.Navigatable;
import com.intellij.util.OpenSourceUtil;
import org.jetbrains.annotations.Nullable;

public abstract class BaseNavigateToSourceAction extends AnAction implements DumbAware {
  private final boolean myFocusEditor;

  protected BaseNavigateToSourceAction(boolean focusEditor) {
    myFocusEditor = focusEditor;
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    OpenSourceUtil.navigate(getNavigatables(dataContext), myFocusEditor);
  }


  public void update(AnActionEvent event){
    DataContext dataContext = event.getDataContext();
    final boolean enabled = isEnabled(dataContext);
    if (ActionPlaces.isPopupPlace(event.getPlace())) {
      event.getPresentation().setVisible(enabled);
    }
    else {
      event.getPresentation().setEnabled(enabled);
    }
  }

  private boolean isEnabled(final DataContext dataContext) {
    Navigatable[] navigatables = getNavigatables(dataContext);
    if (navigatables != null) {
      for (Navigatable navigatable : navigatables) {
        if (navigatable.canNavigate()) return true;
      }
    }
    return false;    
  }

  @Nullable
  protected Navigatable[] getNavigatables(final DataContext dataContext) {
    return PlatformDataKeys.NAVIGATABLE_ARRAY.getData(dataContext);
  }
}
