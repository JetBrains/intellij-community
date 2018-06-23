// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.favoritesTreeView.smartPointerPsiNodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.CompoundProjectViewNodeDecorator;
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
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;

public abstract class BaseSmartPointerPsiNode <Type extends SmartPsiElementPointer> extends ProjectViewNode<Type> implements
                                                                                                                  PsiElementNavigationItem {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.projectView.impl.nodes.BasePsiNode");

  protected BaseSmartPointerPsiNode(Project project, Type value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  @Override
  @NotNull
  public final Collection<AbstractTreeNode> getChildren() {
    PsiElement value = getPsiElement();
    if (value == null) return new ArrayList<>();
    LOG.assertTrue(value.isValid());
    return getChildrenImpl();
  }

  @NotNull
  protected abstract Collection<AbstractTreeNode> getChildrenImpl();

  protected boolean isMarkReadOnly() {
    final Object parentValue = getParentValue();
    return parentValue instanceof PsiDirectory || parentValue instanceof PackageElement;
  }

  @Override
  public PsiElement getTargetElement() {
    VirtualFile file = getVirtualFileForValue();
    if (file == null) {
      return null;
    }
    else {
      return file.isDirectory() ? PsiManager.getInstance(getProject()).findDirectory(file) : PsiManager.getInstance(getProject()).findFile(file);
    }
  }

  private VirtualFile getVirtualFileForValue() {
    PsiElement value = getPsiElement();
    if (value == null) return null;
    return PsiUtilCore.getVirtualFile(value);
  }
  // Should be called in atomic action

  protected abstract void updateImpl(PresentationData data);


  @Override
  public void update(PresentationData data) {
    final PsiElement value = getPsiElement();
    if (value == null || !value.isValid()) {
      setValue(null);
    }
    if (getPsiElement() == null) return;

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
      final PsiElement element = getPsiElement();
      return element instanceof PsiDocCommentOwner
             && element.isValid()
             && ((PsiDocCommentOwner)element).isDeprecated();
    }
    catch (IndexNotReadyException e) {
      return false;
    }
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    if (getPsiElement() == null) return false;
    PsiFile containingFile = getPsiElement().getContainingFile();
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
    return getPsiElement() instanceof NavigationItem && ((NavigationItem)getPsiElement()).canNavigate();
  }

  @Override
  public boolean canNavigateToSource() {
    return getPsiElement() instanceof NavigationItem && ((NavigationItem)getPsiElement()).canNavigateToSource();
  }

  protected PsiElement getPsiElement(){
    final Type value = getValue();
    return value == null ? null : value.getElement();
  }
}
