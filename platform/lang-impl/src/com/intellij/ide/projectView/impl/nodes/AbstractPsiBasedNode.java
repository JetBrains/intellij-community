/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.intellij.ide.projectView.impl.nodes;

import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.ide.bookmarks.Bookmark;
import com.intellij.ide.bookmarks.BookmarkManager;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ProjectViewNodeDecorator;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.ValidateableNode;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.StatePreservingNavigatable;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.RowIcon;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * Class for node descriptors based on PsiElements. Subclasses should define
 * method that extract PsiElement from Value.
 * @param <Value> Value of node descriptor
 */
public abstract class AbstractPsiBasedNode<Value> extends ProjectViewNode<Value> implements ValidateableNode, StatePreservingNavigatable {
  private static final Logger LOG = Logger.getInstance(AbstractPsiBasedNode.class.getName());

  protected AbstractPsiBasedNode(final Project project,
                                final Value value,
                                final ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  @Nullable
  protected abstract PsiElement extractPsiFromValue();
  @Nullable
  protected abstract Collection<AbstractTreeNode> getChildrenImpl();
  protected abstract void updateImpl(final PresentationData data);

  @Override
  @NotNull
  public final Collection<AbstractTreeNode> getChildren() {
    final PsiElement psiElement = extractPsiFromValue();
    if (psiElement == null) {
      return new ArrayList<>();
    }
    final boolean valid = psiElement.isValid();
    if (!LOG.assertTrue(valid)) {
      return Collections.emptyList();
    }

    final Collection<AbstractTreeNode> children = getChildrenImpl();
    return children != null ? children : Collections.<AbstractTreeNode>emptyList();
  }

  @Override
  public boolean isValid() {
    final PsiElement psiElement = extractPsiFromValue();
    return psiElement != null && psiElement.isValid();
  }

  protected boolean isMarkReadOnly() {
    final AbstractTreeNode parent = getParent();
    if (parent == null) {
      return false;
    }
    if (parent instanceof AbstractPsiBasedNode) {
      final PsiElement psiElement = ((AbstractPsiBasedNode)parent).extractPsiFromValue();
      return psiElement instanceof PsiDirectory;
    }

    final Object parentValue = parent.getValue();
    return parentValue instanceof PsiDirectory || parentValue instanceof Module;
  }


  @Override
  public FileStatus getFileStatus() {
    VirtualFile file = getVirtualFileForValue();
    if (file == null) {
      return FileStatus.NOT_CHANGED;
    }
    else {
      return FileStatusManager.getInstance(getProject()).getStatus(file);
    }
  }

  @Nullable
  private VirtualFile getVirtualFileForValue() {
    PsiElement psiElement = extractPsiFromValue();
    if (psiElement == null) {
      return null;
    }
    return PsiUtilBase.getVirtualFile(psiElement);
  }

  // Should be called in atomic action

  @Override
  public void update(final PresentationData data) {
    ApplicationManager.getApplication().runReadAction(() -> {
      if (!validate()) {
        return;
      }

      final PsiElement value = extractPsiFromValue();
      LOG.assertTrue(value.isValid());

      int flags = getIconableFlags();

      try {
        Icon icon = value.getIcon(flags);
        data.setIcon(icon);
      }
      catch (IndexNotReadyException ignored) {
      }
      data.setPresentableText(myName);

      try {
        if (isDeprecated()) {
          data.setAttributesKey(CodeInsightColors.DEPRECATED_ATTRIBUTES);
        }
      }
      catch (IndexNotReadyException ignored) {
      }
      updateImpl(data);
      data.setIcon(patchIcon(myProject, data.getIcon(true), getVirtualFile()));

      for (ProjectViewNodeDecorator decorator : Extensions.getExtensions(ProjectViewNodeDecorator.EP_NAME, myProject)) {
        decorator.decorate(this, data);
      }
    });
  }

  @Iconable.IconFlags
  protected int getIconableFlags() {
    int flags = Iconable.ICON_FLAG_VISIBILITY;
    if (isMarkReadOnly()) {
      flags |= Iconable.ICON_FLAG_READ_STATUS;
    }
    return flags;
  }

  @Nullable
  public static Icon patchIcon(@NotNull Project project, @Nullable Icon original, @Nullable VirtualFile file) {
    if (file == null || original == null) return original;
    
    Icon icon = original;

    final Bookmark bookmarkAtFile = BookmarkManager.getInstance(project).findFileBookmark(file);
    if (bookmarkAtFile != null) {
      final RowIcon composite = new RowIcon(2, RowIcon.Alignment.CENTER);
      composite.setIcon(icon, 0);
      composite.setIcon(bookmarkAtFile.getIcon(), 1);
      icon = composite;
    }

    if (!file.isWritable()) {
      icon = LayeredIcon.create(icon, PlatformIcons.LOCKED_ICON);
    }

    if (file.is(VFileProperty.SYMLINK)) {
      icon = LayeredIcon.create(icon, PlatformIcons.SYMLINK_ICON);
    }

    return icon;
  }
  
  protected boolean isDeprecated() {
    return false;
  }

  @Override
  public boolean contains(@NotNull final VirtualFile file) {
    final PsiElement psiElement = extractPsiFromValue();
    if (psiElement == null || !psiElement.isValid()) {
      return false;
    }

    final PsiFile containingFile = psiElement.getContainingFile();
    if (containingFile == null) {
      return false;
    }
    final VirtualFile valueFile = containingFile.getVirtualFile();
    return valueFile != null && file.equals(valueFile);
  }

  @Nullable
  public NavigationItem getNavigationItem() {
    final PsiElement psiElement = extractPsiFromValue();
    return (psiElement instanceof NavigationItem) ? (NavigationItem) psiElement : null;
  }

  @Override
  public void navigate(boolean requestFocus, boolean preserveState) {
    if (canNavigate()) {
      if (requestFocus || preserveState) {
        NavigationUtil.openFileWithPsiElement(extractPsiFromValue(), requestFocus, requestFocus);
      }
      else {
        getNavigationItem().navigate(requestFocus);
      }
    }
  }

  @Override
  public void navigate(boolean requestFocus) {
    navigate(requestFocus, false);
  }

  @Override
  public boolean canNavigate() {
    final NavigationItem item = getNavigationItem();
    return item != null && item.canNavigate();
  }

  @Override
  public boolean canNavigateToSource() {
    final NavigationItem item = getNavigationItem();
    return item != null && item.canNavigateToSource();
  }

  @Nullable
  protected String calcTooltip() {
    return null;
  }

  @Override
  public boolean validate() {
    final PsiElement psiElement = extractPsiFromValue();
    if (psiElement == null || !psiElement.isValid()) {
      setValue(null);
    }

    return getValue() != null;
  }
}
