// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.memory.agent;

import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.ReferringObject;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiExpression;
import com.intellij.xdebugger.frame.XValueNode;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodePresentationConfigurator;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Function;

public class GCRootReferringObject implements ReferringObject {
  private final MemoryAgentReferenceKind myKind;

  public GCRootReferringObject(@NotNull MemoryAgentReferenceKind kind) {
    this.myKind = kind;
  }

  @Override
  public @NotNull ValueDescriptorImpl createValueDescription(@NotNull Project project, @NotNull Value referee) {
    return new ValueDescriptorImpl(project, null) {
      @Override
      public String getName() {
        return JavaDebuggerBundle.message("ref");
      }

      @Override
      public Value calcValue(EvaluationContextImpl evaluationContext) {
        return null;
      }

      @Override
      public PsiExpression getDescriptorEvaluation(DebuggerContext context) {
        return null;
      }
    };
  }

  @Override
  public final @NotNull Function<XValueNode, XValueNode> getNodeCustomizer() {
    return node -> new XValueNodePresentationConfigurator.ConfigurableXValueNodeImpl() {
      @Override
      public void applyPresentation(@Nullable Icon icon, final @NotNull XValuePresentation valuePresenter, boolean hasChildren) {
        node.setPresentation(icon, new XValuePresentation() {
          @Override
          public @NotNull String getSeparator() {
            return ": ";
          }

          @Override
          public @Nullable String getType() {
            return null;
          }

          @Override
          public void renderValue(@NotNull XValueTextRenderer renderer) {
            String additionalInfo = getAdditionalInfo();
            renderer.renderValue(String.format("%s reference %s", myKind.toString().replace('_', ' '),
                                               additionalInfo == null ? "" : additionalInfo));
          }
        }, hasChildren);
      }
    };
  }

  public @NotNull MemoryAgentReferenceKind getKind() {
    return myKind;
  }

  @Override
  public @NotNull String getNodeName(int order) {
    return "Root";
  }

  @Override
  public @Nullable ObjectReference getReference() {
    return null;
  }

  protected @Nullable String getAdditionalInfo() {
    return null;
  }
}
