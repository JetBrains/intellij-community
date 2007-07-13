package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

/**
 * Author: msk
 */
public abstract class GotoActionBase extends AnAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.actions.GotoActionBase");

  protected static Class myInAction = null;

  public final void actionPerformed(AnActionEvent e) {
    LOG.assertTrue (!getClass ().equals (myInAction));
    try {
      myInAction = getClass();
      gotoActionPerformed (e);
    }
    catch (Throwable t) {
      myInAction = null;
    }
  }

  protected abstract void gotoActionPerformed(AnActionEvent e);

  public final void update(final AnActionEvent event) {
    final Presentation presentation = event.getPresentation();
    final Project project = (Project)event.getDataContext().getData(DataConstants.PROJECT);
    presentation.setEnabled(!getClass ().equals (myInAction) && project != null);
  }
}
