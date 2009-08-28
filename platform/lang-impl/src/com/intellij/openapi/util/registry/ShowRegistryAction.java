package com.intellij.openapi.util.registry;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;

public class ShowRegistryAction extends AnAction implements DumbAware {

  private RegistryUi myUi;

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(myUi == null);
  }

  public void actionPerformed(AnActionEvent e) {
    myUi = new RegistryUi() {
      @Override
      public void dispose() {
        myUi = null;
      }
    };
    myUi.show();
  }
}