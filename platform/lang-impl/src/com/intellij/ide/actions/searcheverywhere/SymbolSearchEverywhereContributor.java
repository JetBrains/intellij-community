// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.NavigationItemListCellRenderer;
import com.intellij.ide.util.gotoByName.ChooseByNameModel;
import com.intellij.ide.util.gotoByName.GotoSymbolModel2;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.ui.IdeUICustomization;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public class SymbolSearchEverywhereContributor extends AbstractGotoSEContributor {

  public SymbolSearchEverywhereContributor(Project project) {
    super(project);
  }

  @NotNull
  @Override
  public String getGroupName() {
    return "Symbols";
  }

  @Override
  public String includeNonProjectItemsText() {
    return IdeBundle.message("checkbox.include.non.project.symbols", IdeUICustomization.getInstance().getProjectConceptName());
  }

  @Override
  public int getSortWeight() {
    return 300;
  }

  @Override
  protected ChooseByNameModel createModel(Project project) {
    return new GotoSymbolModel2(project);
  }

  @Override
  public ListCellRenderer getElementsRenderer() {
    return new NavigationItemListCellRenderer();
  }

  public static class Factory implements SearchEverywhereContributorFactory {
    @Override
    public SearchEverywhereContributor createContributor(AnActionEvent initEvent) {
      return new SymbolSearchEverywhereContributor(initEvent.getProject());
    }
  }

}
