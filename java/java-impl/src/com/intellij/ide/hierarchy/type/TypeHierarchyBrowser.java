// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.hierarchy.type;

import com.intellij.ide.hierarchy.*;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.text.MessageFormat;
import java.util.Comparator;
import java.util.Map;

public class TypeHierarchyBrowser extends TypeHierarchyBrowserBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.hierarchy.type.TypeHierarchyBrowser");

  public TypeHierarchyBrowser(final Project project, final PsiClass psiClass) {
    super(project, psiClass);
  }

  @Override
  protected boolean isInterface(@NotNull PsiElement psiElement) {
    return psiElement instanceof PsiClass && ((PsiClass)psiElement).isInterface();
  }

  @Override
  protected void createTrees(@NotNull Map<String, JTree> trees) {
    createTreeAndSetupCommonActions(trees, IdeActions.GROUP_TYPE_HIERARCHY_POPUP);
  }

  @Override
  protected void prependActions(DefaultActionGroup actionGroup) {
    super.prependActions(actionGroup);
    actionGroup.add(new ChangeScopeAction() {
      @Override
      protected boolean isEnabled() {
        return !Comparing.strEqual(getCurrentViewType(), SUPERTYPES_HIERARCHY_TYPE);
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
  protected boolean isApplicableElement(@NotNull final PsiElement element) {
    return element instanceof PsiClass;
  }

  @Override
  protected Comparator<NodeDescriptor> getComparator() {
    return JavaHierarchyUtil.getComparator(myProject);
  }

  @Override
  protected HierarchyTreeStructure createHierarchyTreeStructure(@NotNull final String typeName, @NotNull final PsiElement psiElement) {
    if (SUPERTYPES_HIERARCHY_TYPE.equals(typeName)) {
      return new SupertypesHierarchyTreeStructure(myProject, (PsiClass)psiElement);
    }
    else if (SUBTYPES_HIERARCHY_TYPE.equals(typeName)) {
      return new SubtypesHierarchyTreeStructure(myProject, (PsiClass)psiElement, getCurrentScopeType());
    }
    else if (TYPE_HIERARCHY_TYPE.equals(typeName)) {
      return new TypeHierarchyTreeStructure(myProject, (PsiClass)psiElement, getCurrentScopeType());
    }
    else {
      LOG.error("unexpected type: " + typeName);
      return null;
    }
  }

  @Override
  protected boolean canBeDeleted(final PsiElement psiElement) {
    return psiElement instanceof PsiClass && !(psiElement instanceof PsiAnonymousClass);
  }

  @Override
  protected String getQualifiedName(final PsiElement psiElement) {
    if (psiElement instanceof PsiClass) {
      return ((PsiClass)psiElement).getQualifiedName();
    }
    return "";
  }

  public static class BaseOnThisTypeAction extends TypeHierarchyBrowserBase.BaseOnThisTypeAction {
    @Override
    protected boolean isEnabled(@NotNull final HierarchyBrowserBaseEx browser, @NotNull final PsiElement psiElement) {
      return super.isEnabled(browser, psiElement) && !CommonClassNames.JAVA_LANG_OBJECT.equals(((PsiClass)psiElement).getQualifiedName());
    }
  }

  @NotNull
  @Override
  protected TypeHierarchyBrowserBase.BaseOnThisTypeAction createBaseOnThisAction() {
    return new BaseOnThisTypeAction();
  }
}