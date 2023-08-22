// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.hierarchy.type;

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.ide.hierarchy.JavaHierarchyUtil;
import com.intellij.ide.hierarchy.TypeHierarchyBrowserBase;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.text.MessageFormat;
import java.util.Comparator;
import java.util.Map;

public class TypeHierarchyBrowser extends TypeHierarchyBrowserBase {
  private static final Logger LOG = Logger.getInstance(TypeHierarchyBrowser.class);

  public TypeHierarchyBrowser(Project project, PsiClass psiClass) {
    super(project, psiClass);
  }

  @Override
  protected boolean isInterface(@NotNull PsiElement psiElement) {
    return psiElement instanceof PsiClass && ((PsiClass)psiElement).isInterface();
  }

  @Override
  protected void createTrees(@NotNull Map<? super @Nls String, ? super JTree> trees) {
    createTreeAndSetupCommonActions(trees, IdeActions.GROUP_TYPE_HIERARCHY_POPUP);
  }

  @Override
  protected void prependActions(@NotNull DefaultActionGroup actionGroup) {
    super.prependActions(actionGroup);
    actionGroup.add(new ChangeScopeAction() {
      @Override
      protected boolean isEnabled() {
        return !Comparing.strEqual(getCurrentViewType(), getSupertypesHierarchyType());
      }
    });
  }

  @Override
  protected String getContentDisplayName(@NotNull String typeName, @NotNull PsiElement element) {
    return MessageFormat.format(typeName, ClassPresentationUtil.getNameForClass((PsiClass)element, false));
  }

  @Override
  protected PsiElement getElementFromDescriptor(@NotNull HierarchyNodeDescriptor descriptor) {
    if (!(descriptor instanceof TypeHierarchyNodeDescriptor)) return null;
    return ((TypeHierarchyNodeDescriptor)descriptor).getPsiClass();
  }

  @Override
  @Nullable
  protected JPanel createLegendPanel() {
    return null;
  }

  @Override
  protected boolean isApplicableElement(@NotNull PsiElement element) {
    return element instanceof PsiClass;
  }

  @Override
  protected boolean isApplicableElementForBaseOn(@NotNull PsiElement element) {
    return element instanceof PsiClass &&
           !CommonClassNames.JAVA_LANG_OBJECT.equals(((PsiClass)element).getQualifiedName());
  }

  @Override
  protected Comparator<NodeDescriptor<?>> getComparator() {
    return JavaHierarchyUtil.getComparator(myProject);
  }

  @Override
  protected HierarchyTreeStructure createHierarchyTreeStructure(@NotNull String typeName, @NotNull PsiElement psiElement) {
    if (getSupertypesHierarchyType().equals(typeName)) {
      return new SupertypesHierarchyTreeStructure(myProject, (PsiClass)psiElement);
    }
    else if (getSubtypesHierarchyType().equals(typeName)) {
      return new SubtypesHierarchyTreeStructure(myProject, (PsiClass)psiElement, getCurrentScopeType());
    }
    else if (getTypeHierarchyType().equals(typeName)) {
      return new TypeHierarchyTreeStructure(myProject, (PsiClass)psiElement, getCurrentScopeType());
    }
    else {
      LOG.error("unexpected type: " + typeName);
      return null;
    }
  }

  @Override
  protected boolean canBeDeleted(PsiElement psiElement) {
    return psiElement instanceof PsiClass && !(psiElement instanceof PsiAnonymousClass);
  }

  @Override
  protected String getQualifiedName(PsiElement psiElement) {
    if (psiElement instanceof PsiClass) {
      return ((PsiClass)psiElement).getQualifiedName();
    }
    return "";
  }
}