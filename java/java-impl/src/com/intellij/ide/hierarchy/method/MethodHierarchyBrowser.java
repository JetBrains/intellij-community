// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.hierarchy.method;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.ide.hierarchy.JavaHierarchyUtil;
import com.intellij.ide.hierarchy.MethodHierarchyBrowserBase;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.ui.PopupHandler;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Comparator;
import java.util.Map;

public class MethodHierarchyBrowser extends MethodHierarchyBrowserBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.hierarchy.method.MethodHierarchyBrowser");

  public MethodHierarchyBrowser(final Project project, final PsiMethod method) {
    super(project, method);
  }

  @Override
  protected void createTrees(@NotNull Map<String, JTree> trees) {
    final JTree tree = createTree(false);
    ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_METHOD_HIERARCHY_POPUP);
    PopupHandler.installPopupHandler(tree, group, ActionPlaces.METHOD_HIERARCHY_VIEW_POPUP, ActionManager.getInstance());
                    
    final BaseOnThisMethodAction action = new BaseOnThisMethodAction();
    action.registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_METHOD_HIERARCHY).getShortcutSet(), tree);

    trees.put(METHOD_TYPE, tree);
  }

  @Override
  protected JPanel createLegendPanel() {
    return createStandardLegendPanel(IdeBundle.message("hierarchy.legend.method.is.defined.in.class"),
                                     IdeBundle.message("hierarchy.legend.method.defined.in.superclass"),
                                     IdeBundle.message("hierarchy.legend.method.should.be.defined"));
  }

  @Override
  protected PsiElement getElementFromDescriptor(@NotNull final HierarchyNodeDescriptor descriptor) {
    if (descriptor instanceof MethodHierarchyNodeDescriptor) {
      return ((MethodHierarchyNodeDescriptor)descriptor).getTargetElement();
    }
    return null;
  }

  @Override
  protected boolean isApplicableElement(@NotNull final PsiElement psiElement) {
    return psiElement instanceof PsiMethod;
  }

  @Override
  protected HierarchyTreeStructure createHierarchyTreeStructure(@NotNull final String typeName, @NotNull final PsiElement psiElement) {
    if (!METHOD_TYPE.equals(typeName)) {
      LOG.error("unexpected type: " + typeName);
      return null;
    }
    return new MethodHierarchyTreeStructure(myProject, (PsiMethod)psiElement, getCurrentScopeType());
  }

  @Override
  protected Comparator<NodeDescriptor> getComparator() {
    return JavaHierarchyUtil.getComparator(myProject);
  }

  public PsiMethod getBaseMethod() {
    return (PsiMethod)getHierarchyBase();
  }

  public static final class BaseOnThisMethodAction extends MethodHierarchyBrowserBase.BaseOnThisMethodAction { }
}