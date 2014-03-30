/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

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

            Project project = CommonDataKeys.PROJECT.getData(dataContext);
            if (project == null) return;

            OpenSourceUtil.openSourcesFrom(dataContext, false);
          }
        }
      }
    );
  }

  public static void install(final JComponent component,
                           @Nullable final Runnable whenPerformed) {
    component.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        DataContext dataContext = DataManager.getInstance().getDataContext(component);
        OpenSourceUtil.openSourcesFrom(dataContext, true);
        if (whenPerformed != null) whenPerformed.run();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_FOCUSED);
  }

  public static void install(@Nullable final Runnable before, final JComponent component,
                           @Nullable final Runnable whenPerformed) {
    component.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        DataContext dataContext = DataManager.getInstance().getDataContext(component);
        if (before != null) before.run();
        OpenSourceUtil.openSourcesFrom(dataContext, true);
        if (whenPerformed != null) whenPerformed.run();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_FOCUSED);
  }
}
