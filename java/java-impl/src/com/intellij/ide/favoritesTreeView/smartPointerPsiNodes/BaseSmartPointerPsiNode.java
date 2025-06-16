// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.favoritesTreeView.smartPointerPsiNodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.CompoundProjectViewNodeDecorator;
import com.intellij.ide.projectView.impl.nodes.BasePsiNode;
import com.intellij.ide.projectView.impl.nodes.PackageElement;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.navigation.NavigationItem;
import com.intellij.navigation.PsiElementNavigationItem;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;

@ApiStatus.Internal
public abstract class BaseSmartPointerPsiNode <Type extends SmartPsiElementPointer> extends ProjectViewNode<Type> implements PsiElementNavigationItem {
  private static final Logger LOG = Logger.getInstance(BasePsiNode.class);

  BaseSmartPointerPsiNode(@NotNull Project project, @NotNull Type value, @NotNull ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  @Override
  public final @NotNull Collection<AbstractTreeNode<?>> getChildren() {
    PsiElement value = getPsiElement();
    if (value == null) return new ArrayList<>();
    LOG.assertTrue(value.isValid());
    return getChildrenImpl();
  }

  protected abstract @NotNull Collection<AbstractTreeNode<?>> getChildrenImpl();

  private boolean isMarkReadOnly() {
    final Object parentValue = getParentValue();
    return parentValue instanceof PsiDirectory || parentValue instanceof PackageElement;
  }

  @Override
  public PsiElement getTargetElement() {
    VirtualFile file = getVirtualFileForValue();
    return PsiUtilCore.findFileSystemItem(getProject(), file);
  }

  private VirtualFile getVirtualFileForValue() {
    PsiElement value = getPsiElement();
    if (value == null) return null;
    return PsiUtilCore.getVirtualFile(value);
  }
  // Should be called in atomic action

  protected abstract void updateImpl(@NotNull PresentationData data);


  @Override
  public void update(@NotNull PresentationData data) {
    final PsiElement value = getPsiElement();
    if (value == null || !value.isValid()) {
      setValue(null);
    }
    if (value == null) return;

    int flags = Iconable.ICON_FLAG_VISIBILITY;
    if (isMarkReadOnly()) {
      flags |= Iconable.ICON_FLAG_READ_STATUS;
    }

    LOG.assertTrue(value.isValid());

    Icon icon = value.getIcon(flags);
    data.setIcon(icon);
    data.setPresentableText(myName);
    if (isDeprecated()) {
      data.setAttributesKey(CodeInsightColors.DEPRECATED_ATTRIBUTES);
    }
    updateImpl(data);
    if (myProject != null) CompoundProjectViewNodeDecorator.get(myProject).decorate(this, data);
  }

  private boolean isDeprecated() {
    try {
      PsiElement psiElement = getPsiElement();
      return psiElement instanceof PsiDocCommentOwner
             && psiElement.isValid()
             && ((PsiDocCommentOwner)psiElement).isDeprecated();
    }
    catch (IndexNotReadyException e) {
      return false;
    }
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    PsiElement psiElement = getPsiElement();
    if (psiElement == null) return false;
    PsiFile containingFile = psiElement.getContainingFile();
    return file.equals(containingFile.getVirtualFile());
  }

  @Override
  public void navigate(boolean requestFocus) {
    if (canNavigate()) {
      ((NavigationItem)getPsiElement()).navigate(requestFocus);
    }
  }

  @Override
  public boolean canNavigate() {
    PsiElement psiElement = getPsiElement();
    return psiElement instanceof NavigationItem && ((NavigationItem)psiElement).canNavigate();
  }

  @Override
  public boolean canNavigateToSource() {
    PsiElement psiElement = getPsiElement();
    return psiElement instanceof NavigationItem && ((NavigationItem)psiElement).canNavigateToSource();
  }

  protected PsiElement getPsiElement(){
    //noinspection CastToIncompatibleInterface
    return (PsiElement)getValue(); // automatically de-anchorized in AbstractTreeNode.getValue
  }
}
