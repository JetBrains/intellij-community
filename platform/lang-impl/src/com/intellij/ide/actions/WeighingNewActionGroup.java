/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;

/**
 * @author peter
 */
public class WeighingNewActionGroup extends WeighingActionGroup {
  private ActionGroup myDelegate;

  protected ActionGroup getDelegate() {
    if (myDelegate == null) {
      myDelegate = (ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_NEW);
      getTemplatePresentation().setText(myDelegate.getTemplatePresentation().getText());
      setPopup(myDelegate.isPopup());
    }
    return myDelegate;
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    e.getPresentation().setText(getTemplatePresentation().getText());
  }

  @Override
  protected boolean shouldBeChosenAnyway(AnAction action) {
    final Class<? extends AnAction> aClass = action.getClass();
    return aClass == CreateFileAction.class || aClass == CreateDirectoryOrPackageAction.class ||
           "NewModuleInGroupAction".equals(aClass.getSimpleName()); //todo why is it in idea module?
  }
}
