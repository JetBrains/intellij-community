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
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.ui.impl.watch.UserExpressionDescriptorImpl;
import com.intellij.openapi.util.Pair;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeInplaceEditor;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeState;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;

import java.util.List;

/**
 * @author egor
 */
public class CustomFieldInplaceEditor extends XDebuggerTreeInplaceEditor {
  private final EnumerationChildrenRenderer myRenderer;

  public CustomFieldInplaceEditor(XDebuggerTreeNode node, UserExpressionDescriptorImpl descriptor, EnumerationChildrenRenderer renderer) {
    super(node, "customField");
    myRenderer = renderer;
    myExpressionEditor.setExpression(TextWithImportsImpl.toXExpression(descriptor.getEvaluationText()));
  }

  @Override
  public void doOKAction() {
    int index = myNode.getParent().getIndex(myNode);
    List<Pair<String, TextWithImports>> children = myRenderer.getChildren();
    Pair<String, TextWithImports> old = children.get(index);
    children.set(index, Pair.create(old.first, TextWithImportsImpl.fromXExpression(myExpressionEditor.getExpression())));

    if (myTree.isDetached()) {
      myTree.rebuildAndRestore(XDebuggerTreeState.saveState(myTree));
    }
    XDebuggerUtilImpl.rebuildAllSessionsViews(getProject());

    super.doOKAction();
  }
}
