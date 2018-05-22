// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.gotoByName.ChooseByNameModel;
import com.intellij.ide.util.gotoByName.GotoFileModel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.ui.IdeUICustomization;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.InputEvent;

/**
 * @author Konstantin Bulenkov
 */
public class FileSearchEverywhereContributor extends AbstractGotoSEContributor {

  @NotNull
  @Override
  public String getGroupName() {
    return "Files";
  }

  @Override
  public String includeNonProjectItemsText() {
    return IdeBundle.message("checkbox.include.non.project.files", IdeUICustomization.getInstance().getProjectConceptName());
  }

  @Override
  public int getSortWeight() {
    return 200;
  }

  @Override
  protected ChooseByNameModel createModel(Project project) {
    return new GotoFileModel(project){
      @Override
      public boolean isSlashlessMatchingEnabled() {
        return false;
      }
    };
  }

  @Override
  public ListCellRenderer getElementsRenderer(Project project) {
    return createModel(project).getListCellRenderer();
  }

  @Override
  public boolean processSelectedItem(Project project, Object selected, int modifiers) {
    //todo maybe another elements types
    if (selected instanceof PsiElement) {
      NavigationUtil.activateFileWithPsiElement((PsiElement) selected, (modifiers & InputEvent.SHIFT_MASK) != 0);
    }

    return true;
  }
}
