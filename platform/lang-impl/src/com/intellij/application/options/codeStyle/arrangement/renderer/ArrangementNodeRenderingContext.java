/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.application.options.codeStyle.arrangement.renderer;

import com.intellij.psi.codeStyle.arrangement.model.*;
import com.intellij.openapi.util.Ref;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.Map;

/**
 * // TODO den add doc
 * <p/>
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/8/12 11:26 AM
 */
public class ArrangementNodeRenderingContext {

  private final Map<NodeType, Stack<ArrangementNodeRenderer<?>>> myFree
    = new EnumMap<NodeType, Stack<ArrangementNodeRenderer<?>>>(NodeType.class);
  private final Map<NodeType, Stack<ArrangementNodeRenderer<?>>> myBusy
    = new EnumMap<NodeType, Stack<ArrangementNodeRenderer<?>>>(NodeType.class);

  public ArrangementNodeRenderingContext() {
    for (NodeType type : NodeType.values()) {
      myFree.put(type, new Stack<ArrangementNodeRenderer<?>>());
      myBusy.put(type, new Stack<ArrangementNodeRenderer<?>>());
    }
  }

  @SuppressWarnings("unchecked")
  @NotNull
  public <T extends ArrangementSettingsNode> ArrangementNodeRenderer<T> getRenderer(@NotNull T node) {
    final Ref<ArrangementNodeRenderer<?>> result = new Ref<ArrangementNodeRenderer<?>>();
    node.invite(new ArrangementSettingsNodeVisitor() {
      @Override
      public void visit(@NotNull ArrangementSettingsAtomNode node) {
        result.set(getRenderer(NodeType.ATOM)); 
      }

      @Override
      public void visit(@NotNull ArrangementSettingsCompositeNode node) {
        NodeType type = node.getOperator() == ArrangementSettingsCompositeNode.Operator.AND ? NodeType.AND : NodeType.OR;
        result.set(getRenderer(type)); 
      }
    });
    return (ArrangementNodeRenderer<T>)result.get();
  }

  @SuppressWarnings("unchecked")
  private <T extends ArrangementSettingsNode> ArrangementNodeRenderer<T> getRenderer(@NotNull NodeType type) {
    Stack<ArrangementNodeRenderer<?>> freeRenderers = myFree.get(type);
    ArrangementNodeRenderer<T> result;
    if (freeRenderers.isEmpty()) {
      result = type.createRenderer(this);
    }
    else {
      result = (ArrangementNodeRenderer<T>)freeRenderers.pop();
    }

    myBusy.get(type).push(result);
    return result;
  }

  // TODO den add doc
  public void reset() {
    for (Map.Entry<NodeType, Stack<ArrangementNodeRenderer<?>>> entry : myBusy.entrySet()) {
      myFree.get(entry.getKey()).addAll(entry.getValue());
      for (ArrangementNodeRenderer<?> renderer : entry.getValue()) {
        renderer.reset();
      }
      entry.getValue().clear();
    }
  }
  
  @SuppressWarnings("unchecked")
  private enum NodeType {
    AND
      {
        @Override
        public <T extends ArrangementSettingsNode> ArrangementNodeRenderer<T> createRenderer(
          @NotNull ArrangementNodeRenderingContext context)
        {
          return (ArrangementNodeRenderer<T>)new ArrangementAndRenderer(context);
        }
      },
    ATOM
      {
        @Override
        public <T extends ArrangementSettingsNode> ArrangementNodeRenderer<T> createRenderer(
          @NotNull ArrangementNodeRenderingContext context)
        {
          return (ArrangementNodeRenderer<T>)new ArrangementAtomRenderer();
        }
      },
    OR
      {
        @Override
        public <T extends ArrangementSettingsNode> ArrangementNodeRenderer<T> createRenderer(
          @NotNull ArrangementNodeRenderingContext context)
        {
          return (ArrangementNodeRenderer<T>)new ArrangementOrRenderer();
        }
      };

    public abstract <T extends ArrangementSettingsNode> ArrangementNodeRenderer<T> createRenderer(
      @NotNull ArrangementNodeRenderingContext context
    );
  }
}
