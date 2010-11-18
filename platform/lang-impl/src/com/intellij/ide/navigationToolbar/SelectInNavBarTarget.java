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
package com.intellij.ide.navigationToolbar;

import com.intellij.ide.DataManager;
import com.intellij.ide.SelectInManager;
import com.intellij.ide.StandardTargetWeights;
import com.intellij.ide.impl.SelectInTargetPsiWrapper;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.IdeRootPaneNorthExtension;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.openapi.wm.impl.IdeRootPane;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

/**
 * User: anna
 * Date: 09-Nov-2005
 */
public class SelectInNavBarTarget extends SelectInTargetPsiWrapper implements DumbAware {
  public SelectInNavBarTarget(final Project project) {
    super(project);
  }

  public String toString() {
    return SelectInManager.NAV_BAR;
  }

  @NonNls
  public String getToolWindowId() {
    return "NavBar";
  }

  public String getMinorViewId() {
    return null;
  }

  public float getWeight() {
    return StandardTargetWeights.NAV_BAR;
  }

  protected boolean canSelect(PsiFileSystemItem file) {
    return UISettings.getInstance().SHOW_NAVIGATION_BAR;
  }

  protected void select(final Object selector, VirtualFile virtualFile, final boolean requestFocus) {
    selectTail();
  }

  private static void selectTail() {
    DataManager.getInstance().getDataContextFromFocus().doWhenDone(new AsyncResult.Handler<DataContext>() {
      @Override
      public void run(DataContext context) {
        IdeFrame frame = IdeFrame.KEY.getData(context);
        if (frame != null) {
          IdeRootPaneNorthExtension navBarExt = frame.getNorthExtension(NavBarRootPaneExtension.NAV_BAR);
          if (navBarExt != null) {
            JComponent c = navBarExt.getComponent();
            NavBarPanel panel = (NavBarPanel)c.getClientProperty("NavBarPanel");
            panel.selectTail();
          }
        }
      }
    });
  }

  protected boolean canWorkWithCustomObjects() {
    return false;
  }

  protected void select(PsiElement element, boolean requestFocus) {
    selectTail();
  }

}
