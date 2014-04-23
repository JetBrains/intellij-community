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
package com.intellij.debugger.engine;

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorProvider;
import com.intellij.debugger.ui.impl.watch.NodeManagerImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.debugger.ui.tree.*;
import com.intellij.debugger.ui.tree.render.ChildrenBuilder;
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener;
import com.intellij.debugger.ui.tree.render.NodeRenderer;
import com.intellij.xdebugger.frame.*;
import com.sun.jdi.Type;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
* @author egor
*/
public class JavaValue extends XNamedValue implements NodeDescriptorProvider {
  private final ValueDescriptorImpl myValueDescriptor;
  private final EvaluationContextImpl myEvaluationContext;
  private final NodeManagerImpl myNodeManager;

  JavaValue(ValueDescriptorImpl valueDescriptor, EvaluationContextImpl evaluationContext, NodeManagerImpl nodeManager) {
    super(valueDescriptor.getName());
    myValueDescriptor = valueDescriptor;
    myEvaluationContext = evaluationContext;
    myValueDescriptor.setContext(evaluationContext);
    myValueDescriptor.updateRepresentation(evaluationContext, DescriptorLabelListener.DUMMY_LISTENER);
    myNodeManager = nodeManager;
  }

  @Override
  public NodeDescriptorImpl getDescriptor() {
    return myValueDescriptor;
  }

  @Override
  public void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
    Type type = myValueDescriptor.getType();
    String typeName = type != null ? type.name() : "";
    node.setPresentation(null, typeName, myValueDescriptor.getValueText(), myValueDescriptor.isExpandable());
  }

  @Override
  public void computeChildren(@NotNull final XCompositeNode node) {
    myEvaluationContext.getDebugProcess().getManagerThread().schedule(new DebuggerCommandImpl() {
      @Override
      protected void action() throws Exception {
        final XValueChildrenList children = new XValueChildrenList();
        final NodeRenderer renderer = myValueDescriptor.getRenderer(myEvaluationContext.getDebugProcess());
        renderer.buildChildren(myValueDescriptor.getValue(), new ChildrenBuilder() {
          @Override
          public NodeDescriptorFactory getDescriptorManager() {
            return myNodeManager;
          }

          @Override
          public NodeManager getNodeManager() {
            return myNodeManager;
            //return new NodeManagerImpl(myEvaluationContext.getDebugProcess().getProject(), null) {
            //  @Override
            //  public DebuggerTreeNodeImpl createMessageNode(String s) {
            //    return null;
            //  }
            //
            //  @Override
            //  public DebuggerTreeNodeImpl createNode(NodeDescriptor nodeDescriptor, EvaluationContext evaluationContext) {
            //    if (nodeDescriptor instanceof ValueDescriptorImpl) {
            //      children.add(new JavaValue((ValueDescriptorImpl)nodeDescriptor, (EvaluationContextImpl)evaluationContext, myNodeManager));
            //    }
            //    return null;
            //  }
            //
            //  @Override
            //  public <T extends NodeDescriptor> T getDescriptor(NodeDescriptor parent, DescriptorData<T> key) {
            //    return myNodeManager.getDescriptor(parent, key);
            //  }
            //};
          }

          @Override
          public ValueDescriptor getParentDescriptor() {
            return myValueDescriptor;
          }

          @Override
          public void setChildren(List<DebuggerTreeNode> nodes) {
            for (DebuggerTreeNode node : nodes) {
              NodeDescriptor descriptor = node.getDescriptor();
              if (descriptor instanceof ValueDescriptorImpl) {
                children.add(new JavaValue((ValueDescriptorImpl)descriptor, myEvaluationContext, myNodeManager));
              }
            }
          }
        }, myEvaluationContext);
        node.addChildren(children, true);
    }});
  }
}
