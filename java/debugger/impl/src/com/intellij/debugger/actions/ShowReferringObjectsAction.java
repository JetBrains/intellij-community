/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.ui.impl.watch.NodeManagerImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.psi.PsiExpression;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.XInspectDialog;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author egor
 */
public class ShowReferringObjectsAction extends XDebuggerTreeActionBase {
  private static final long MAX_REFERRING = 100;

  @Override
  protected void perform(XValueNodeImpl node, @NotNull String nodeName, AnActionEvent e) {
    XValue container = node.getValueContainer();
    if (container instanceof JavaValue) {
      JavaValue javaValue = ((JavaValue)container);
      XDebuggerTree tree = XDebuggerTree.getTree(e.getDataContext());
      XInspectDialog dialog = new XInspectDialog(tree.getProject(),
                                                 tree.getEditorsProvider(),
                                                 tree.getSourcePosition(),
                                                 nodeName,
                                                 new ReferringObjectsValue(javaValue),
                                                 tree.getValueMarkers());
      dialog.setTitle("Referring objects for " + nodeName);
      dialog.show();
    }
  }

  private static class ReferringObjectsValue extends JavaValue {
    private ReferringObjectsValue(JavaValue parent,
                                 @NotNull ValueDescriptorImpl valueDescriptor,
                                 @NotNull EvaluationContextImpl evaluationContext,
                                 NodeManagerImpl nodeManager) {
      super(parent, valueDescriptor, evaluationContext, nodeManager, false);
    }

    public ReferringObjectsValue(JavaValue javaValue) {
      super(null, javaValue.getDescriptor(), javaValue.getEvaluationContext(), null, false);
    }

    @Override
    public boolean canNavigateToSource() {
      return false;
    }

    @Override
    public void computeChildren(@NotNull final XCompositeNode node) {
      getEvaluationContext().getDebugProcess().getManagerThread().schedule(
        new SuspendContextCommandImpl(getEvaluationContext().getSuspendContext()) {
          @Override
          public Priority getPriority() {
            return Priority.NORMAL;
          }

          @Override
          public void contextAction() throws Exception {
            final XValueChildrenList children = new XValueChildrenList();

            Value value = getDescriptor().getValue();
            List<ObjectReference> references = ((ObjectReference)value).referringObjects(MAX_REFERRING);
            int i = 1;
            for (final ObjectReference reference : references) {
              ValueDescriptorImpl descriptor = new ValueDescriptorImpl(getProject(), reference) {
                @Override
                public Value calcValue(EvaluationContextImpl evaluationContext) throws EvaluateException {
                  return reference;
                }

                @Override
                public String getName() {
                  return "Ref";
                }

                @Override
                public String calcValueName() {
                  return "Ref";
                }

                @Override
                public PsiExpression getDescriptorEvaluation(DebuggerContext context) throws EvaluateException {
                  return null;
                }
              };
              children.add("Referrer " + i++ , new ReferringObjectsValue(null, descriptor, getEvaluationContext(), null));
            }

            node.addChildren(children, true);
          }
        }
      );
    }
  }
}
