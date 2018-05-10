// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.SearchEverywhereClassifier;
import com.intellij.ide.util.NavigationItemListCellRenderer;
import com.intellij.ide.util.gotoByName.ChooseByNameModel;
import com.intellij.ide.util.gotoByName.ChooseByNamePopup;
import com.intellij.ide.util.gotoByName.GotoClassModel2;
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
public class ClassSearchEverywhereContributor implements SearchEverywhereContributor {
  @NotNull
  @Override
  public String getSearchProviderId() {
    return getClass().getSimpleName();
  }

  @NotNull
  @Override
  public String getGroupName() {
    return "Classes";
  }

  @Override
  public String includeNonProjectItemsText() {
    return IdeBundle.message("checkbox.include.non.project.classes", IdeUICustomization.getInstance().getProjectConceptName());
  }

  @Override
  public int getSortWeight() {
    return 100;
  }

  public ContributorSearchResult search(Project project, String pattern, boolean everywhere, ProgressIndicator progressIndicator, int elementsLimit) {
    ChooseByNameModel mdl = new GotoClassModel2(project);
    ChooseByNamePopup popup = ChooseByNamePopup.createPopup(project, mdl, (PsiElement)null);
    List<Object> items = new ArrayList<>();
    boolean[] hasMore = {false}; //todo builder for  ContributorSearchResult #UX-1
    popup.getProvider().filterElements(popup, pattern, everywhere, progressIndicator, o -> {
      if (SearchEverywhereClassifier.EP_Manager.isClass(o) && !items.contains(o)) {
        if (elementsLimit >=0 && items.size() >= elementsLimit) {
          hasMore[0] = true;
          return false;
        }
        items.add(o);

      }
      return true;
    });

    return new ContributorSearchResult(items, hasMore[0]);
  }

  @Override
  public ListCellRenderer getElementsRenderer(Project project) {
    return new NavigationItemListCellRenderer();
  }

  @Override
  public void processSelectedItem(Object selected, int modifiers) {
    if (selected instanceof PsiElement) {
      NavigationUtil.activateFileWithPsiElement((PsiElement) selected, (modifiers & InputEvent.SHIFT_MASK) != 0);
    }
  }
}
