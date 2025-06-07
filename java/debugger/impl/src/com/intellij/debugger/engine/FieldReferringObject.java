// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.ui.impl.watch.FieldDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiExpression;
import com.intellij.xdebugger.frame.XValueNode;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodePresentationConfigurator;
import com.sun.jdi.Field;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Function;

public class FieldReferringObject implements ReferringObject {
  private final @NotNull ObjectReference myReference;
  private final @NotNull Field myField;

  public FieldReferringObject(@NotNull ObjectReference reference, @NotNull Field field) {
    myReference = reference;
    myField = field;
  }

  @Override
  public @NotNull ValueDescriptorImpl createValueDescription(@NotNull Project project, @NotNull Value referee) {
    return new FieldDescriptorImpl(project, myReference, myField) {
      @Override
      public Value calcValue(EvaluationContextImpl evaluationContext) {
        return myReference;
      }

      @Override
      public PsiExpression getDescriptorEvaluation(DebuggerContext context) throws EvaluateException {
        throw new NeedMarkException(myReference);
      }
    };
  }

  @Override
  public @Nullable String getNodeName(int order) {
    return null;
  }

  @Override
  public @NotNull ObjectReference getReference() {
    return myReference;
  }

  @Override
  public @NotNull Function<XValueNode, XValueNode> getNodeCustomizer() {
    return node -> new XValueNodePresentationConfigurator.ConfigurableXValueNodeImpl() {
      @Override
      public void applyPresentation(@Nullable Icon icon, final @NotNull XValuePresentation valuePresenter, boolean hasChildren) {
        node.setPresentation(icon, new XValuePresentation() {
          @Override
          public @NotNull String getSeparator() {
            return " in ";
          }

          @Override
          public @Nullable String getType() {
            return valuePresenter.getType();
          }

          @Override
          public void renderValue(@NotNull XValueTextRenderer renderer) {
            valuePresenter.renderValue(renderer);
          }
        }, hasChildren);
      }
    };
  }
}
