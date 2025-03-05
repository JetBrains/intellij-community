// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.memory.agent;

import com.intellij.debugger.engine.ReferringObject;
import com.intellij.xdebugger.frame.XValueNode;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodePresentationConfigurator;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Function;

public abstract class MemoryAgentReferringObject implements ReferringObject {
  protected final @NotNull ObjectReference myReference;
  protected final boolean myIsWeakSoftReachable;

  public MemoryAgentReferringObject(@NotNull ObjectReference reference, boolean isWeakSoftReachable) {
    this.myReference = reference;
    this.myIsWeakSoftReachable = isWeakSoftReachable;
  }

  @Override
  public final @NotNull Function<XValueNode, XValueNode> getNodeCustomizer() {
    return node -> new XValueNodePresentationConfigurator.ConfigurableXValueNodeImpl() {
      @Override
      public void applyPresentation(@Nullable Icon icon, final @NotNull XValuePresentation valuePresenter, boolean hasChildren) {
        node.setPresentation(icon, new XValuePresentation() {
          @Override
          public @NotNull String getSeparator() {
            return MemoryAgentReferringObject.this.getSeparator();
          }

          @Override
          public @Nullable String getType() {
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
    };
  }

  @Override
  public @NotNull ObjectReference getReference() { return myReference; }

  public @NotNull String getSeparator() { return " = "; }
}
