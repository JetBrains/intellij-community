package com.intellij.util;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * @author lesya
 */

public class EditSourceOnEnterKeyHandler{
  public static void install(final JTree tree){
    tree.addKeyListener(
      new KeyAdapter() {
        public void keyPressed(KeyEvent e) {
          if (KeyEvent.VK_ENTER == e.getKeyCode()) {
            DataContext dataContext = DataManager.getInstance().getDataContext(tree);

            Project project = (Project)dataContext.getData(DataConstants.PROJECT);
            if (project == null) return;

            OpenSourceUtil.openSourcesFrom(dataContext, false);
          }
        }
      }
    );
  }

  public static void install(final JComponent component,
                           final Runnable whenPerformed) {
    component.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        DataContext dataContext = DataManager.getInstance().getDataContext(component);
        OpenSourceUtil.openSourcesFrom(dataContext, true);
        if (whenPerformed != null) whenPerformed.run();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_FOCUSED);
  }

}
