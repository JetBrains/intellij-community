// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navigationToolbar;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.StandardTargetWeights;
import com.intellij.ide.impl.SelectInTargetPsiWrapper;
import com.intellij.ide.navbar.ui.StaticNavBarPanel;
import com.intellij.ide.navbar.vm.NavBarVm;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ex.IdeFrameEx;
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Anna Kozlova
 * @author Konstantin Bulenkov
 */
final class SelectInNavBarTarget extends SelectInTargetPsiWrapper implements DumbAware {
  public static final String NAV_BAR_ID = "NavBar";

  SelectInNavBarTarget(@NotNull Project project) {
    super(project);
  }

  @Override
  @NonNls
  public String getToolWindowId() {
    return NAV_BAR_ID;
  }

  @Override
  protected boolean canSelect(final PsiFileSystemItem file) {
    return UISettings.getInstance().getShowNavigationBar();
  }

  @Override
  protected void select(final Object selector, final VirtualFile virtualFile, final boolean requestFocus) {
    selectInNavBar(false);
  }

  @Override
  protected void select(final PsiElement element, boolean requestFocus) {
    selectInNavBar(false);
  }

  public static void selectInNavBar(boolean showPopup) {
    DataManager.getInstance().getDataContextFromFocus()
      .doWhenDone((Consumer<DataContext>)context -> {
        IdeFrame frame = IdeFrame.KEY.getData(context);
        if (frame instanceof IdeFrameEx) {
          var navBar = ((IdeFrameEx)frame).getNorthExtension(IdeStatusBarImpl.NAVBAR_WIDGET_KEY);
          if (navBar != null) {
            Object panel = navBar.getClientProperty(NavBarRootPaneExtension.PANEL_KEY);
            if (panel instanceof StaticNavBarPanel navBarPanel) {
              NavBarVm vm = navBarPanel.getModel();
              if (vm != null) {
                vm.selectTail();
                if (showPopup) {
                  vm.showPopup();
                }
              }
            }
            else {
              ((NavBarPanel)panel).rebuildAndSelectLastDirectoryOrTail(showPopup);
            }
          }
        }
      });
  }

  @Override
  public float getWeight() {
    return StandardTargetWeights.NAV_BAR_WEIGHT;
  }

  @Override
  public String getMinorViewId() {
    return null;
  }

  public String toString() {
    return IdeBundle.message("navigation.bar");
  }
}
