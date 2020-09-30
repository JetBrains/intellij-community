// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent;

import com.intellij.debugger.engine.ReferringObject;
import com.intellij.xdebugger.frame.XFullValueEvaluator;
import com.intellij.xdebugger.frame.XValueNode;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodePresentationConfigurator;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Function;

public abstract class MemoryAgentReferringObject implements ReferringObject {
  @NotNull protected final ObjectReference myReference;
  protected final boolean myIsWeakSoftReachable;

  public MemoryAgentReferringObject(@NotNull ObjectReference reference, boolean isWeakSoftReachable) {
    this.myReference = reference;
    this.myIsWeakSoftReachable = isWeakSoftReachable;
  }

  @NotNull
  @Override
  public final Function<XValueNode, XValueNode> getNodeCustomizer() {
    return node -> new XValueNodePresentationConfigurator.ConfigurableXValueNodeImpl() {
      @Override
      public void applyPresentation(@Nullable Icon icon, @NotNull final XValuePresentation valuePresenter, boolean hasChildren) {
        node.setPresentation(icon, new XValuePresentation() {
          @NotNull
          @Override
          public String getSeparator() {
            return MemoryAgentReferringObject.this.getSeparator();
          }

          @Nullable
          @Override
          public String getType() {
            return null;
          }

          @Override
          public void renderValue(@NotNull XValueTextRenderer renderer) {
            if (myIsWeakSoftReachable) {
              renderer.renderKeywordValue("Weak/Soft reachable ");
            }

            String type = valuePresenter.getType();
            if (type != null) {
              renderer.renderComment(String.format("{%s} ", type));
            }
            valuePresenter.renderValue(renderer);
          }
        }, hasChildren);
      }

      @Override
      public void setFullValueEvaluator(@NotNull XFullValueEvaluator fullValueEvaluator) {
      }
    };
  }

  @NotNull
  @Override
  public ObjectReference getReference() { return myReference; }

  @NotNull
  public String getSeparator() { return " = "; }
}
