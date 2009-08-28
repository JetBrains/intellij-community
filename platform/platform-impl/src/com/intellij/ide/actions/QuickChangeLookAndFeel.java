package com.intellij.ide.actions;

import com.intellij.ide.ui.LafManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;

import javax.swing.*;

/**
 * @author max
 */
public class QuickChangeLookAndFeel extends QuickSwitchSchemeAction {
  protected void fillActions(Project project, DefaultActionGroup group) {
    final LafManager manager = LafManager.getInstance();
    final UIManager.LookAndFeelInfo[] lfs = manager.getInstalledLookAndFeels();
    final UIManager.LookAndFeelInfo current = manager.getCurrentLookAndFeel();
    for (int i = 0; i < lfs.length; i++) {
      final UIManager.LookAndFeelInfo lf = lfs[i];
      group.add(new AnAction(lf.getName(), "", lf == current ? ourCurrentAction : ourNotCurrentAction) {
        public void actionPerformed(AnActionEvent e) {
          manager.setCurrentLookAndFeel(lf);
          manager.updateUI();
        }
      });
    }
  }

  protected boolean isEnabled() {
    return LafManager.getInstance().getInstalledLookAndFeels().length > 1;
  }
}
