/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.slicer;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.*;

/**
 * User: cdr
 */
public class CanItBeNullAction  extends AnAction {
  private final SliceTreeBuilder myTreeBuilder;
  private static final String TEXT = "Can it be null?";

  public CanItBeNullAction(SliceTreeBuilder treeBuilder) {
    super(TEXT, "Determine whether null can flow into this expression", IconLoader.getIcon("/debugger/db_disabled_breakpoint_process.png"));
    myTreeBuilder = treeBuilder;
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setText(TEXT + (myTreeBuilder.analysisInProgress ? " (Analysis in progress)" : ""));
    e.getPresentation().setEnabled(isAvailable());
  }

  private boolean isAvailable() {
    if (myTreeBuilder.analysisInProgress) return false;
    if (!myTreeBuilder.dataFlowToThis) return false;
    if (myTreeBuilder.splitByLeafExpressions) return false;
    SliceRootNode rootNode = (SliceRootNode)myTreeBuilder.getRootNode().getUserObject();
    PsiElement element = rootNode == null ? null : rootNode.getRootUsage().getUsageInfo().getElement();
    PsiType type;
    if (element instanceof PsiVariable) {
      type = ((PsiVariable)element).getType();
    }
    else if (element instanceof PsiExpression) {
      type = ((PsiExpression)element).getType();
    }
    else {
      type = null;
    }
    return type instanceof PsiClassType || type instanceof PsiArrayType;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    myTreeBuilder.switchToLeafNulls();
  }
}
