package com.intellij.openapi.util.registry;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;

public class ShowRegistryAction extends AnAction {

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