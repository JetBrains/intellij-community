/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.ide.projectView.SettingsProvider;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.ProjectTreeStructure;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.file.PsiDirectoryImpl;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class FavoritesTreeStructure extends ProjectTreeStructure {

  private static final Logger LOGGER = Logger.getInstance(FavoritesTreeStructure.class);
  private TreeStructureProvider myNonProjectProvider = null;
  public FavoritesTreeStructure(Project project) {
    super(project, FavoritesProjectViewPane.ID);
    myNonProjectProvider = new MyProvider(project);
  }

  @Override
  protected AbstractTreeNode createRoot(final Project project, ViewSettings settings) {
    return new FavoritesRootNode(project);
  }

  public void rootsChanged() {
    ((FavoritesRootNode)getRootElement()).rootsChanged();
  }


  @Override
  public Object[] getChildElements(Object element) {
    if (!(element instanceof AbstractTreeNode)) {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    final AbstractTreeNode favTreeElement = (AbstractTreeNode)element;
    try {
      if (!(element instanceof FavoritesListNode)) {
        Object[] elements = super.getChildElements(favTreeElement);
        if (elements.length > 0) return elements;

        ViewSettings settings = favTreeElement instanceof SettingsProvider ? ((SettingsProvider)favTreeElement).getSettings() : ViewSettings.DEFAULT;
        return ArrayUtil.toObjectArray(myNonProjectProvider.modify(favTreeElement, new ArrayList<>(), settings));
      }

      final List<AbstractTreeNode> result = new ArrayList<>();
      final FavoritesListNode listNode = (FavoritesListNode)element;
      if (listNode.getProvider() != null) {
        return ArrayUtil.toObjectArray(listNode.getChildren());
      }
      final Collection<AbstractTreeNode> roots = FavoritesListNode.getFavoritesRoots(myProject, listNode.getName(), listNode);
      for (AbstractTreeNode<?> abstractTreeNode : roots) {
        final Object value = abstractTreeNode.getValue();

        if (value == null) continue;
        if (value instanceof PsiElement && !((PsiElement)value).isValid()) continue;
        if (value instanceof SmartPsiElementPointer && ((SmartPsiElementPointer)value).getElement() == null) continue;

        boolean invalid = false;
        for (FavoriteNodeProvider nodeProvider : Extensions.getExtensions(FavoriteNodeProvider.EP_NAME, myProject)) {
          if (nodeProvider.isInvalidElement(value)) {
            invalid = true;
            break;
          }
        }
        if (invalid) continue;

        result.add(abstractTreeNode);
      }
      //myFavoritesRoots = result;
      //if (result.isEmpty()) {
      //  result.add(getEmptyScreen());
      //}
      return ArrayUtil.toObjectArray(result);
    }
    catch (Exception e) {
      LOGGER.error(e);
    }

    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  private AbstractTreeNode<String> getEmptyScreen() {
    return new AbstractTreeNode<String>(myProject, IdeBundle.message("favorites.empty.screen")) {
      @Override
      @NotNull
      public Collection<AbstractTreeNode> getChildren() {
        return Collections.emptyList();
      }

      @Override
      public void update(final PresentationData presentation) {
        presentation.setPresentableText(getValue());
      }
    };
  }

  @Override
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

  @Override
  @NotNull
  public NodeDescriptor createDescriptor(Object element, NodeDescriptor parentDescriptor) {
    return new FavoritesTreeNodeDescriptor(myProject, parentDescriptor, (AbstractTreeNode)element);
  }

  private static class MyProvider implements TreeStructureProvider {
    private Project myProject;

    public MyProvider(Project project) {
      myProject = project;
    }

    @NotNull
    @Override
    public Collection<AbstractTreeNode> modify(@NotNull AbstractTreeNode parent,
                                               @NotNull Collection<AbstractTreeNode> children,
                                               ViewSettings settings) {
      if (parent instanceof PsiDirectoryNode && children.isEmpty()) {
        VirtualFile virtualFile = ((PsiDirectoryNode)parent).getVirtualFile();
        if (virtualFile == null) return children;
        VirtualFile[] virtualFiles = virtualFile.getChildren();
        List<AbstractTreeNode> result = new ArrayList<>();
        for (VirtualFile file : virtualFiles) {
          AbstractTreeNode child;
          if (file.isDirectory()) child = new PsiDirectoryNode(myProject, new PsiDirectoryImpl((PsiManagerImpl)PsiManager.getInstance(myProject), file), settings);
          else child = new PsiFileNode(myProject, PsiUtilCore.getPsiFile(myProject, file), settings);
          child.setParent(parent);
          result.add(child);
        }
        return result;
      }
      return children;
    }

    @Nullable
    @Override
    public Object getData(Collection<AbstractTreeNode> selected, String dataName) {
      return null;
    }
  }
}
