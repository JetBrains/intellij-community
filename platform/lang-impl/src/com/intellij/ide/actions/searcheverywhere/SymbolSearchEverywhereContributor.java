// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.SearchEverywhereClassifier;
import com.intellij.ide.util.NavigationItemListCellRenderer;
import com.intellij.ide.util.gotoByName.ChooseByNameItemProvider;
import com.intellij.ide.util.gotoByName.ChooseByNameModel;
import com.intellij.ide.util.gotoByName.ChooseByNamePopup;
import com.intellij.ide.util.gotoByName.GotoSymbolModel2;
import com.intellij.navigation.PsiElementNavigationItem;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.IdeUICustomization;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class SymbolSearchEverywhereContributor implements SearchEverywhereContributor {
  @NotNull
  @Override
  public String getSearchProviderId() {
    return getClass().getSimpleName();
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
  public ContributorSearchResult search(Project project,
                                        String pattern,
                                        boolean everywhere,
                                        ProgressIndicator progressIndicator,
                                        int elementsLimit) {
    final GlobalSearchScope scope = getProjectScope(project);
    ChooseByNameModel model = new GotoSymbolModel2(project);
    ChooseByNamePopup popup = ChooseByNamePopup.createPopup(project, model, (PsiElement)null);
    final ChooseByNameItemProvider provider = popup.getProvider();

    List<Object> symbols = new ArrayList<>();
    boolean[] hasMore = {false};
    provider.filterElements(popup, pattern, everywhere,progressIndicator, o -> {
        if (SearchEverywhereClassifier.EP_Manager.isSymbol(o) && !symbols.contains(o)) {
          PsiElement element = null;
          if (o instanceof PsiElement) {
            element = (PsiElement)o;
          }
          else if (o instanceof PsiElementNavigationItem) {
            element = ((PsiElementNavigationItem)o).getTargetElement();
          }
          VirtualFile virtualFile = SearchEverywhereClassifier.EP_Manager.getVirtualFile(o);
          //some elements are non-physical like DB columns
          boolean isElementWithoutFile = element != null && element.getContainingFile() == null;
          boolean isFileInScope = virtualFile != null && (everywhere || scope.accept(virtualFile));
          boolean isSpecialElement = element == null && virtualFile == null; //all Rider elements don't have any psi elements within
          if (isElementWithoutFile || isFileInScope || isSpecialElement) {
            symbols.add(o);
          }
        }
        hasMore[0] = symbols.size() >= elementsLimit;
        return !hasMore[0];
      });

    return new ContributorSearchResult(symbols, hasMore[0]);
  }

  @NotNull
  private static GlobalSearchScope getProjectScope(@NotNull Project project) {
    final GlobalSearchScope scope = SearchEverywhereClassifier.EP_Manager.getProjectScope(project);
    if (scope != null) return scope;
    return GlobalSearchScope.projectScope(project);
  }

  @Override
  public ListCellRenderer getElementsRenderer(Project project) {
    return new NavigationItemListCellRenderer();
  }

  @Override
  public void processSelectedItem(Object selected, int modifiers) {
    //todo maybe another elements types
    if (selected instanceof PsiElement) {
      NavigationUtil.activateFileWithPsiElement((PsiElement) selected, (modifiers & InputEvent.SHIFT_MASK) != 0);
    }
  }
}
