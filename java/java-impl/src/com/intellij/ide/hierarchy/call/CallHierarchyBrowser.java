// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.hierarchy.call;

import com.intellij.ide.hierarchy.CallHierarchyBrowserBase;
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.ide.hierarchy.JavaHierarchyUtil;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.ui.PopupHandler;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Comparator;
import java.util.Map;

public class CallHierarchyBrowser extends CallHierarchyBrowserBase {
  private static final Logger LOG = Logger.getInstance(CallHierarchyBrowser.class);

  public CallHierarchyBrowser(@NotNull Project project, @NotNull PsiMember method) {
    super(project, method);
  }

  /**
   * @deprecated use CallHierarchyBrowser#CallHierarchyBrowser(Project, PsiMember)
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
  public CallHierarchyBrowser(@NotNull Project project, @NotNull PsiMethod method) {
    super(project, method);
  }

  @Override
  protected void createTrees(@NotNull final Map<String, JTree> type2TreeMap) {
    ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_CALL_HIERARCHY_POPUP);
    final JTree tree1 = createTree(false);
    PopupHandler.installPopupHandler(tree1, group, ActionPlaces.CALL_HIERARCHY_VIEW_POPUP, ActionManager.getInstance());
    final BaseOnThisMethodAction baseOnThisMethodAction = new BaseOnThisMethodAction();
    baseOnThisMethodAction
      .registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_CALL_HIERARCHY).getShortcutSet(), tree1);
    type2TreeMap.put(getCalleeType(), tree1);

    final JTree tree2 = createTree(false);
    PopupHandler.installPopupHandler(tree2, group, ActionPlaces.CALL_HIERARCHY_VIEW_POPUP, ActionManager.getInstance());
    baseOnThisMethodAction
      .registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_CALL_HIERARCHY).getShortcutSet(), tree2);
    type2TreeMap.put(getCallerType(), tree2);
  }

  @Override
  protected PsiElement getElementFromDescriptor(@NotNull HierarchyNodeDescriptor descriptor) {
    if (descriptor instanceof CallHierarchyNodeDescriptor) {
      CallHierarchyNodeDescriptor nodeDescriptor = (CallHierarchyNodeDescriptor)descriptor;
      return nodeDescriptor.getEnclosingElement();
    }
    return null;
  }

  @Override
  protected PsiElement getOpenFileElementFromDescriptor(@NotNull HierarchyNodeDescriptor descriptor) {
    if (descriptor instanceof CallHierarchyNodeDescriptor) {
      CallHierarchyNodeDescriptor nodeDescriptor = (CallHierarchyNodeDescriptor)descriptor;
      return nodeDescriptor.getTargetElement();
    }
    return null;
  }

  @Override
  protected boolean isApplicableElement(@NotNull PsiElement e) {
    return e instanceof PsiMethod || e instanceof PsiField;
  }

  @Override
  protected HierarchyTreeStructure createHierarchyTreeStructure(@NotNull final String typeName, @NotNull final PsiElement psiElement) {
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
