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

package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.SelectInTarget;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author cdr
 */
public class FavoritesProjectViewPane extends AbstractProjectViewPane {
  @NonNls public static final String ID = "Favorites";
  private FavoritesTreeViewPanel myViewPanel;
  private final ProjectView myProjectView;
  private final FavoritesManager myFavoritesManager;
  private final FavoritesManager.FavoritesListener myFavoritesListener;

  protected FavoritesProjectViewPane(final Project project, ProjectView projectView, FavoritesManager favoritesManager) {
    super(project);
    myProjectView = projectView;
    myFavoritesManager = favoritesManager;
    myFavoritesListener = new FavoritesManager.FavoritesListener() {
      public void rootsChanged(String listName) {
      }
      public void listAdded(String listName) {
        refreshMySubIdsAndSelect(listName);
      }

      public void listRemoved(String listName) {
        String selectedSubId = getSubId();
        refreshMySubIdsAndSelect(selectedSubId);
      }

      private void refreshMySubIdsAndSelect(String listName) {
        myFavoritesManager.removeFavoritesListener(myFavoritesListener);
        myProjectView.removeProjectPane(FavoritesProjectViewPane.this);
        myProjectView.addProjectPane(FavoritesProjectViewPane.this);
        myFavoritesManager.addFavoritesListener(myFavoritesListener);

        if (ArrayUtil.find(myFavoritesManager.getAvailableFavoritesLists(), listName) == -1) {
          listName = null;
        }
        myProjectView.changeView(ID, listName);
      }
    };
    myFavoritesManager.addFavoritesListener(myFavoritesListener);
  }

  public String getTitle() {
    return IdeBundle.message("action.toolwindow.favorites");
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/general/toolWindowFavorites.png");
  }

  @NotNull
  public String getId() {
    return ID;
  }

  public void installComparator() {
    final ProjectView projectView = ProjectView.getInstance(myProject);
    getTreeBuilder().setNodeDescriptorComparator(new FavoritesComparator(projectView, ID));
  }

  public JComponent createComponent() {
    //if (myViewPanel != null) return myViewPanel;

    myViewPanel = new FavoritesTreeViewPanel(myProject, null, getSubId());
    myTree = myViewPanel.getTree();
    setTreeBuilder(myViewPanel.getBuilder());
    myTreeStructure = myViewPanel.getFavoritesTreeStructure();
    installComparator();
    return myViewPanel;
  }

  public void dispose() {
    myViewPanel = null;
    myFavoritesManager.removeFavoritesListener(myFavoritesListener);
    super.dispose();
  }

  @NotNull
  public String[] getSubIds() {
    return myFavoritesManager.getAvailableFavoritesLists();
  }

  @NotNull
  public String getPresentableSubIdName(@NotNull final String subId) {
    return subId;
  }

  public ActionCallback updateFromRoot(boolean restoreExpandedPaths) {
    return ((FavoritesViewTreeBuilder) getTreeBuilder()).updateFromRootCB();
  }

  public void select(Object object, VirtualFile file, boolean requestFocus) {
    if (!(object instanceof PsiElement)) return;
    /*PsiElement element = (PsiElement)object;
    PsiFile psiFile = element.getContainingFile();
    if (psiFile != null) {
      element = psiFile;
    }

    if (element instanceof PsiJavaFile) {
      final PsiClass[] classes = ((PsiJavaFile)element).getClasses();
      if (classes.length > 0) {
        element = classes[0];
      }
    }

    final PsiElement originalElement = element.getOriginalElement();*/
    final VirtualFile virtualFile = PsiUtilBase.getVirtualFile((PsiElement)object);
    final String list = FavoritesViewSelectInTarget.findSuitableFavoritesList(virtualFile, myProject, getSubId());
    if (list == null) return;
    if (!list.equals(getSubId())) {
      ProjectView.getInstance(myProject).changeView(ID, list);
    }
    myViewPanel.selectElement(object, virtualFile, requestFocus);
  }

  public int getWeight() {
    return 4;
  }

  public SelectInTarget createSelectInTarget() {
    return new FavoritesViewSelectInTarget(myProject);
  }

  public void addToolbarActions(final DefaultActionGroup group) {
    group.add(ActionManager.getInstance().getAction(IdeActions.RENAME_FAVORITES_LIST)); 
  }
}
