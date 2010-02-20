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
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.AbstractUrl;
import com.intellij.ide.projectView.impl.ProjectTreeStructure;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * User: anna
 * Date: Feb 15, 2005
 */
public class FavoritesTreeStructure extends ProjectTreeStructure {
  private final FavoritesManager myFavoritesManager;
  private final String myListName;

  public FavoritesTreeStructure(Project project, @NotNull final String name) {
    super(project, FavoritesProjectViewPane.ID);
    myListName = name;
    myFavoritesManager = FavoritesManager.getInstance(project);
  }

  protected AbstractTreeNode createRoot(final Project project, ViewSettings settings) {
    return new FavoritesRootNode();
  }

  public void rootsChanged() {
    ((FavoritesRootNode)getRootElement()).rootsChanged();
  }


  //for tests only
  @NotNull public Collection<AbstractTreeNode> getFavoritesRoots() {
    List<Pair<AbstractUrl, String>> urls = myFavoritesManager.getFavoritesListRootUrls(myListName);
    if (urls == null) return Collections.emptyList();
    return createFavoritesRoots(urls);
  }

  public Object[] getChildElements(Object element) {
    if (!(element instanceof AbstractTreeNode)) {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
    final AbstractTreeNode favoritesTreeElement = (AbstractTreeNode)element;
    try {
      if (element != getRootElement()) {
        return super.getChildElements(favoritesTreeElement);
      }
      Set<AbstractTreeNode> result = new HashSet<AbstractTreeNode>();
      for (AbstractTreeNode<?> abstractTreeNode : getFavoritesRoots()) {
        final Object val = abstractTreeNode.getValue();
        if (val == null) {
          continue;
        }
        if (val instanceof PsiElement && !((PsiElement)val).isValid()) {
          continue;
        }
        if (val instanceof SmartPsiElementPointer && ((SmartPsiElementPointer)val).getElement() == null) {
          continue;
        }
        boolean isInvalid = false;
        for(FavoriteNodeProvider nodeProvider: Extensions.getExtensions(FavoriteNodeProvider.EP_NAME, myProject)) {
          if (nodeProvider.isInvalidElement(val)) {
            isInvalid = true;
            break;
          }
        }
        if (isInvalid) continue;

        result.add(abstractTreeNode);
      }
      //myFavoritesRoots = result;
      if (result.isEmpty()) {
        result.add(getEmptyScreen());
      }
      return ArrayUtil.toObjectArray(result);
    }
    catch (Exception e) {
    }

    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  private AbstractTreeNode<String> getEmptyScreen() {
    return new AbstractTreeNode<String>(myProject, IdeBundle.message("favorites.empty.screen")) {
      @NotNull
      public Collection<AbstractTreeNode> getChildren() {
        return Collections.emptyList();
      }

      public void update(final PresentationData presentation) {
        presentation.setPresentableText(getValue());
      }
    };
  }

  public Object getParentElement(Object element) {
    AbstractTreeNode parent = null;
    if (element == getRootElement()) {
      return null;
    }
    if (element instanceof AbstractTreeNode) {
      parent = ((AbstractTreeNode)element).getParent();
    }
    if (parent == null) {
      return getRootElement();
    }
    return parent;
  }

  @NotNull
  public NodeDescriptor createDescriptor(Object element, NodeDescriptor parentDescriptor) {
    return new FavoritesTreeNodeDescriptor(myProject, parentDescriptor, (AbstractTreeNode)element);
  }

  @NotNull private Collection<AbstractTreeNode> createFavoritesRoots(@NotNull List<Pair<AbstractUrl,String>> urls) {
    List<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();
    for (Pair<AbstractUrl, String> pair : urls) {
      AbstractUrl abstractUrl = pair.getFirst();
      final Object[] path = abstractUrl.createPath(myProject);
      if (path == null || path.length < 1 || path[0] == null) {
        continue;
      }
      try {
        String className = pair.getSecond();
        Class<? extends AbstractTreeNode> nodeClass = (Class<? extends AbstractTreeNode>)Class.forName(className);
        AbstractTreeNode node = ProjectViewNode.createTreeNode(nodeClass, myProject, path[path.length - 1], this);
        result.add(node);
      }
      catch (Exception e) {
      }
    }
    return result;
  }

  private class FavoritesRootNode extends AbstractTreeNode<String> {
    private Collection<AbstractTreeNode> myFavoritesRoots;

    public FavoritesRootNode() {
      super(FavoritesTreeStructure.this.myProject, "");
    }

    @NotNull
    public Collection<AbstractTreeNode> getChildren() {
      if (myFavoritesRoots == null) {
        myFavoritesRoots = getFavoritesRoots();
      }
      return myFavoritesRoots;
    }

    public void rootsChanged() {
      myFavoritesRoots = null;
    }

    public void update(final PresentationData presentation) {
    }
  }
}
