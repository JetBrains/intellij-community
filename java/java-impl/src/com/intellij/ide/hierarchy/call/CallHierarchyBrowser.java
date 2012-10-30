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
import com.intellij.psi.PsiMethod;
import com.intellij.ui.PopupHandler;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Comparator;
import java.util.Map;

public class CallHierarchyBrowser extends CallHierarchyBrowserBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.hierarchy.call.CallHierarchyBrowser");

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
    type2TreeMap.put(CALLEE_TYPE, tree1);

    final JTree tree2 = createTree(false);
    PopupHandler.installPopupHandler(tree2, group, ActionPlaces.CALL_HIERARCHY_VIEW_POPUP, ActionManager.getInstance());
    baseOnThisMethodAction
      .registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_CALL_HIERARCHY).getShortcutSet(), tree2);
    type2TreeMap.put(CALLER_TYPE, tree2);
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
  protected boolean isApplicableElement(@NotNull final PsiElement element) {
    return element instanceof PsiMethod;
  }

  @Override
  protected HierarchyTreeStructure createHierarchyTreeStructure(@NotNull final String typeName, @NotNull final PsiElement psiElement) {
    if (CALLER_TYPE.equals(typeName)) {
      return new CallerMethodsTreeStructure(myProject, (PsiMethod)psiElement, getCurrentScopeType());
    }
    else if (CALLEE_TYPE.equals(typeName)) {
      return new CalleeMethodsTreeStructure(myProject, (PsiMethod)psiElement, getCurrentScopeType());
    }
    else {
      LOG.error("unexpected type: " + typeName);
      return null;
    }
  }

  @Override
  protected Comparator<NodeDescriptor> getComparator() {
    return JavaHierarchyUtil.getComparator(myProject);
  }

  public static final class BaseOnThisMethodAction extends CallHierarchyBrowserBase.BaseOnThisMethodAction {
  }
}
