// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.hierarchy.call;

import com.intellij.ide.hierarchy.CallHierarchyBrowserBase;
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.ide.hierarchy.JavaHierarchyUtil;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.ui.PopupHandler;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Comparator;
import java.util.Map;

public class CallHierarchyBrowser extends CallHierarchyBrowserBase {
  private static final Logger LOG = Logger.getInstance(CallHierarchyBrowser.class);

  public CallHierarchyBrowser(@NotNull Project project, @NotNull PsiMember method) {
    super(project, method);
  }

  @Override
  protected void createTrees(@NotNull Map<? super @Nls String, ? super JTree> type2TreeMap) {
    JTree tree1 = createTree(false);
    PopupHandler.installPopupMenu(tree1, IdeActions.GROUP_CALL_HIERARCHY_POPUP, ActionPlaces.CALL_HIERARCHY_VIEW_POPUP);
    BaseOnThisMethodAction baseOnThisMethodAction = new BaseOnThisMethodAction();
    baseOnThisMethodAction
      .registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_CALL_HIERARCHY).getShortcutSet(), tree1);
    type2TreeMap.put(getCalleeType(), tree1);

    JTree tree2 = createTree(false);
    PopupHandler.installPopupMenu(tree2, IdeActions.GROUP_CALL_HIERARCHY_POPUP, ActionPlaces.CALL_HIERARCHY_VIEW_POPUP);
    baseOnThisMethodAction
      .registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_CALL_HIERARCHY).getShortcutSet(), tree2);
    type2TreeMap.put(getCallerType(), tree2);
  }

  @Override
  protected PsiElement getElementFromDescriptor(@NotNull HierarchyNodeDescriptor descriptor) {
    if (descriptor instanceof CallHierarchyNodeDescriptor nodeDescriptor) {
      return nodeDescriptor.getEnclosingElement();
    }
    return null;
  }

  @Override
  protected PsiElement getOpenFileElementFromDescriptor(@NotNull HierarchyNodeDescriptor descriptor) {
    if (descriptor instanceof CallHierarchyNodeDescriptor nodeDescriptor) {
      return nodeDescriptor.getTargetElement();
    }
    return null;
  }

  @Override
  protected boolean isApplicableElement(@NotNull PsiElement e) {
    return e instanceof PsiMethod || e instanceof PsiField || e instanceof PsiRecordComponent
           || e instanceof PsiClass aClass && aClass.isRecord();
  }

  @Override
  protected HierarchyTreeStructure createHierarchyTreeStructure(@NotNull String typeName, @NotNull PsiElement psiElement) {
    if (getCallerType().equals(typeName)) {
      return new CallerMethodsTreeStructure(myProject, (PsiMember)psiElement, getCurrentScopeType());
    }
    if (getCalleeType().equals(typeName)) {
      return new CalleeMethodsTreeStructure(myProject, (PsiMember)psiElement, getCurrentScopeType());
    }
    else {
      LOG.error("unexpected type: " + typeName);
      return null;
    }
  }

  @Override
  protected Comparator<NodeDescriptor<?>> getComparator() {
    return JavaHierarchyUtil.getComparator(myProject);
  }

  public static final class BaseOnThisMethodAction extends CallHierarchyBrowserBase.BaseOnThisMethodAction {
  }
}
