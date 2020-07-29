// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public final class EditSourceOnEnterKeyHandler{
  public static void install(@NotNull JTree tree) {
    tree.addKeyListener(
      new KeyAdapter() {
        @Override
        public void keyPressed(KeyEvent e) {
          if (KeyEvent.VK_ENTER != e.getKeyCode()) return;

          DataContext dataContext = DataManager.getInstance().getDataContext(tree);
          Project project = CommonDataKeys.PROJECT.getData(dataContext);
          if (project == null) return;

          OpenSourceUtil.openSourcesFrom(dataContext, false);
        }
      }
    );
  }

  public static void install(@NotNull JComponent component, @Nullable Runnable whenPerformed) {
    component.registerKeyboardAction(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        DataContext dataContext = DataManager.getInstance().getDataContext(component);

        OpenSourceUtil.openSourcesFrom(dataContext, true);
        if (whenPerformed != null) whenPerformed.run();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_FOCUSED);
  }
}
