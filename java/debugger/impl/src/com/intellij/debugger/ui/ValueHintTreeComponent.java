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
package com.intellij.debugger.ui;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.ui.impl.InspectDebuggerTree;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeExpression;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.xdebugger.impl.evaluate.quick.common.AbstractValueHintTreeComponent;

/**
 * @author nik
*/
class ValueHintTreeComponent extends AbstractValueHintTreeComponent<Pair<NodeDescriptorImpl, String>> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.ValueHintTreeComponent");
  private final ValueHint myValueHint;
  private final InspectDebuggerTree myTree;

  public ValueHintTreeComponent(final ValueHint valueHint, InspectDebuggerTree tree, final String title) {
    super(valueHint, tree, Pair.create(tree.getInspectDescriptor(), title));
    myValueHint = valueHint;
    myTree = tree;
  }


  @Override
  protected void updateTree(Pair<NodeDescriptorImpl, String> descriptorWithTitle){
    final NodeDescriptorImpl descriptor = descriptorWithTitle.first;
    final String title = descriptorWithTitle.second;
    final DebuggerContextImpl context = (DebuggerManagerEx.getInstanceEx(myValueHint.getProject())).getContext();
    context.getDebugProcess().getManagerThread().schedule(new DebuggerContextCommandImpl(context) {
      @Override
      public void threadAction() {
        myTree.setInspectDescriptor(descriptor);
        myValueHint.showTreePopup(myTree, context, title, ValueHintTreeComponent.this);
      }
    });
  }


  @Override
  protected void setNodeAsRoot(final Object node) {
    if (node instanceof DebuggerTreeNodeImpl) {
      myValueHint.shiftLocation();
      final DebuggerTreeNodeImpl debuggerTreeNode = (DebuggerTreeNodeImpl)node;
      final DebuggerContextImpl context = (DebuggerManagerEx.getInstanceEx(myValueHint.getProject())).getContext();
      context.getDebugProcess().getManagerThread().schedule(new DebuggerContextCommandImpl(context) {
        @Override
        public void threadAction() {
          try {
            final NodeDescriptorImpl descriptor = debuggerTreeNode.getDescriptor();
            final TextWithImports evaluationText = DebuggerTreeNodeExpression.createEvaluationText(debuggerTreeNode, context);
            final String title = evaluationText.getText();
            addToHistory(Pair.create(descriptor, title));
            myTree.setInspectDescriptor(descriptor);
            myValueHint.showTreePopup(myTree, context, title, ValueHintTreeComponent.this);
          }
          catch (final EvaluateException e1) {
            LOG.debug(e1);
          }
        }
      });
    }
  }

}
