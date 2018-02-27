/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

  protected boolean isInterface(@NotNull PsiElement psiElement) {
    return psiElement instanceof PsiClass && ((PsiClass)psiElement).isInterface();
  }

  protected void createTrees(@NotNull Map<String, JTree> trees) {
    createTreeAndSetupCommonActions(trees, IdeActions.GROUP_TYPE_HIERARCHY_POPUP);
  }

  protected void prependActions(DefaultActionGroup actionGroup) {
    super.prependActions(actionGroup);
    actionGroup.add(new ChangeScopeAction() {
      protected boolean isEnabled() {
        return !Comparing.strEqual(getCurrentViewType(), SUPERTYPES_HIERARCHY_TYPE);
      }
    });
  }

  @Override
  protected String getContentDisplayName(@NotNull String typeName, @NotNull PsiElement element) {
    return MessageFormat.format(typeName, ClassPresentationUtil.getNameForClass((PsiClass)element, false));
  }

  protected PsiElement getElementFromDescriptor(@NotNull HierarchyNodeDescriptor descriptor) {
    if (!(descriptor instanceof TypeHierarchyNodeDescriptor)) return null;
    return ((TypeHierarchyNodeDescriptor)descriptor).getPsiClass();
  }

  @Nullable
  protected JPanel createLegendPanel() {
    return null;
  }

  protected boolean isApplicableElement(@NotNull final PsiElement element) {
    return element instanceof PsiClass;
  }

  protected Comparator<NodeDescriptor> getComparator() {
    return JavaHierarchyUtil.getComparator(myProject);
  }

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

  protected boolean canBeDeleted(final PsiElement psiElement) {
    return psiElement instanceof PsiClass && !(psiElement instanceof PsiAnonymousClass);
  }

  protected String getQualifiedName(final PsiElement psiElement) {
    if (psiElement instanceof PsiClass) {
      return ((PsiClass)psiElement).getQualifiedName();
    }
    return "";
  }

  public static class BaseOnThisTypeAction extends TypeHierarchyBrowserBase.BaseOnThisTypeAction {
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