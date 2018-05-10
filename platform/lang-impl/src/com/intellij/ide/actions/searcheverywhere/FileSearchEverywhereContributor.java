// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.gotoByName.ChooseByNameModel;
import com.intellij.ide.util.gotoByName.ChooseByNamePopup;
import com.intellij.ide.util.gotoByName.GotoFileModel;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.ui.IdeUICustomization;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class FileSearchEverywhereContributor implements SearchEverywhereContributor {
  @NotNull
  @Override
  public String getSearchProviderId() {
    return getClass().getSimpleName();
  }

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
  public ContributorSearchResult search(Project project, String pattern, boolean everywhere, ProgressIndicator progressIndicator, int elementsLimit) {
    ChooseByNameModel mdl = createModel(project);

    ChooseByNamePopup popup = ChooseByNamePopup.createPopup(project, mdl, (PsiElement)null);
    List<Object> items = new ArrayList<>();
    boolean[] hasMore = {false}; //todo builder for  ContributorSearchResult #UX-1
    popup.getProvider().filterElements(popup, pattern, everywhere,
                                       progressIndicator, o -> {

        if (o != null && !items.contains(o)) {
          if (elementsLimit >= 0 && items.size() >= elementsLimit) {
            hasMore[0] = true;
            return false;
          }
          items.add(o);
        }
        return true;
      });

    return new ContributorSearchResult(items, hasMore[0]);
  }

  @NotNull
  private GotoFileModel createModel(Project project) {
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
  public void processSelectedItem(Object selected, int modifiers) {
    //todo maybe another elements types
    if (selected instanceof PsiElement) {
      NavigationUtil.activateFileWithPsiElement((PsiElement) selected, (modifiers & InputEvent.SHIFT_MASK) != 0);
    }
  }
}
