/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.concurrency.ResultConsumer;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeExpression;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.xdebugger.impl.evaluate.quick.common.DebuggerTreeCreator;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
*/
class DebuggerTreeCreatorImpl implements DebuggerTreeCreator<Pair<NodeDescriptorImpl, String>> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.ValueHintTreeComponent");
  private final Project myProject;

  public DebuggerTreeCreatorImpl(Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public String getTitle(@NotNull Pair<NodeDescriptorImpl, String> descriptor) {
    return descriptor.getSecond();
  }

  @Override
  public void createDescriptorByNode(Object node, final ResultConsumer<Pair<NodeDescriptorImpl, String>> resultConsumer) {
    if (node instanceof DebuggerTreeNodeImpl) {
      final DebuggerTreeNodeImpl debuggerTreeNode = (DebuggerTreeNodeImpl)node;
      final DebuggerContextImpl context = DebuggerManagerEx.getInstanceEx(myProject).getContext();
      context.getDebugProcess().getManagerThread().schedule(new DebuggerContextCommandImpl(context) {
        @Override
        public void threadAction(@NotNull SuspendContextImpl suspendContext) {
          try {
            final TextWithImports evaluationText = DebuggerTreeNodeExpression.createEvaluationText(debuggerTreeNode, context);
            resultConsumer.onSuccess(Pair.create(debuggerTreeNode.getDescriptor(), evaluationText.getText()));
          }
          catch (EvaluateException e) {
            resultConsumer.onFailure(e);
          }
        }
      });
    }
  }

  @NotNull
  @Override
  public Tree createTree(@NotNull Pair<NodeDescriptorImpl, String> descriptor) {
    return ValueHint.createInspectTree(descriptor.first, myProject);
  }
}
