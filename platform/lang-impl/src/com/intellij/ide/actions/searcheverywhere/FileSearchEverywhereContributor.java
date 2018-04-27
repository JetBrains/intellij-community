// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.SearchEverywhereClassifier;
import com.intellij.ide.util.gotoByName.ChooseByNameModel;
import com.intellij.ide.util.gotoByName.ChooseByNamePopup;
import com.intellij.ide.util.gotoByName.GotoFileModel;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.IdeUICustomization;
import org.jetbrains.annotations.NotNull;

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
    final GlobalSearchScope scope = getProjectScope(project);

    ChooseByNameModel mdl = new GotoFileModel(project){
      @Override
      public boolean isSlashlessMatchingEnabled() {
        return false;
      }
    };

    ChooseByNamePopup popup = ChooseByNamePopup.createPopup(project, mdl, (PsiElement)null);
    List<Object> items = new ArrayList<>();
    boolean[] hasMore = {false}; //todo builder for  ContributorSearchResult #UX-1
    popup.getProvider().filterElements(popup, pattern, true,
                                       progressIndicator, o -> {
        VirtualFile file = null;
        if (o instanceof VirtualFile) {
          file = (VirtualFile)o;
        } else if (o instanceof PsiFile) {
          file = ((PsiFile)o).getVirtualFile();
        } else if (o instanceof PsiDirectory) {
          file = ((PsiDirectory)o).getVirtualFile();
        }
        if (file != null
            && !(pattern.indexOf(' ') != -1 && file.getName().indexOf(' ') == -1)
            && (everywhere || scope.accept(file))
            && !items.contains(file)) {
          if (elementsLimit >= 0 && items.size() >= elementsLimit) {
            hasMore[0] = true;
            return false;
          }
          items.add(file);
        }
        return true;
      });

        return new ContributorSearchResult(items, hasMore[0]);
  }

  @NotNull
  private static GlobalSearchScope getProjectScope(@NotNull Project project) {
    final GlobalSearchScope scope = SearchEverywhereClassifier.EP_Manager.getProjectScope(project);
    if (scope != null) return scope;
    return GlobalSearchScope.projectScope(project);
  }
}
