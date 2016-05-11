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

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.impl.nodes.LibraryGroupElement;
import com.intellij.ide.projectView.impl.nodes.NamedLibraryElement;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.PresentableNodeDescriptor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * User: anna
 * Date: Feb 15, 2005
 */
public class FavoritesTreeNodeDescriptor extends PresentableNodeDescriptor<AbstractTreeNode> {
  private final AbstractTreeNode myElement;
  public static final FavoritesTreeNodeDescriptor[] EMPTY_ARRAY = new FavoritesTreeNodeDescriptor[0];

  public FavoritesTreeNodeDescriptor(final Project project, final NodeDescriptor parentDescriptor, final AbstractTreeNode element) {
    super(project, parentDescriptor);
    myElement = element;
  }

  @Override
  protected void update(PresentationData presentation) {
    myElement.update();
    presentation.copyFrom(myElement.getPresentation());
  }

 /* protected boolean isMarkReadOnly() {
    final Object parentValue = myElement.getParent() == null ? null : myElement.getParent().getValue();
    return parentValue instanceof PsiDirectory || parentValue instanceof PackageElement;
  }*/

  public String getLocation() {
    return getLocation(myElement, myProject);
  }

  public static String getLocation(final AbstractTreeNode element, @NotNull final Project project) {
    Object nodeElement = element.getValue();
    if (nodeElement instanceof SmartPsiElementPointer) {
      nodeElement = ((SmartPsiElementPointer)nodeElement).getElement();
    }
    if (nodeElement instanceof PsiElement) {
      if (nodeElement instanceof PsiDirectory) {
        return VfsUtilCore.getRelativeLocation(((PsiDirectory)nodeElement).getVirtualFile(), project.getBaseDir());
      }
      if (nodeElement instanceof PsiFile) {
        final PsiFile containingFile = (PsiFile)nodeElement;
        return VfsUtilCore.getRelativeLocation(containingFile.getVirtualFile(), project.getBaseDir());
      }
    }

    if (nodeElement instanceof LibraryGroupElement) {
      return ((LibraryGroupElement)nodeElement).getModule().getName();
    }
    if (nodeElement instanceof NamedLibraryElement) {
      final NamedLibraryElement namedLibraryElement = ((NamedLibraryElement)nodeElement);
      final Module module = namedLibraryElement.getModule();
      return (module != null ? module.getName() : "") + ":" + namedLibraryElement.getOrderEntry().getPresentableName();
    }
    if (nodeElement instanceof File) {
      return VfsUtilCore.getRelativeLocation(VfsUtil.findFileByIoFile((File)nodeElement, false), project.getBaseDir());
    }

    final FavoriteNodeProvider[] nodeProviders = Extensions.getExtensions(FavoriteNodeProvider.EP_NAME, project);
    for (FavoriteNodeProvider provider : nodeProviders) {
      String location = provider.getElementLocation(nodeElement);
      if (location != null) return location;
    }
    return null;
  }

  @Override
  public AbstractTreeNode getElement() {
    return myElement;
  }

  public boolean equals(Object object) {
    if (!(object instanceof FavoritesTreeNodeDescriptor)) return false;
    return ((FavoritesTreeNodeDescriptor)object).getElement().equals(myElement);
  }

  public int hashCode() {
    return myElement.hashCode();
  }

  @Nullable
  public FavoritesTreeNodeDescriptor getFavoritesRoot() {
    FavoritesTreeNodeDescriptor descriptor = this;
    while (descriptor != null && descriptor.getParentDescriptor() instanceof FavoritesTreeNodeDescriptor) {
      FavoritesTreeNodeDescriptor parent = (FavoritesTreeNodeDescriptor)descriptor.getParentDescriptor();
      if (parent != null && parent.getParentDescriptor() == null) {
        return descriptor;
      }
      descriptor = parent;
    }
    return descriptor;
  }

  @Override
  public PresentableNodeDescriptor getChildToHighlightAt(int index) {
    return myElement.getChildToHighlightAt(index);
  }
}
