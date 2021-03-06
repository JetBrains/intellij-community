// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine;

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.ui.impl.watch.FieldDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.frame.XFullValueEvaluator;
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
  @NotNull private final ObjectReference myReference;
  @NotNull private final Field myField;

  public FieldReferringObject(@NotNull ObjectReference reference, @NotNull Field field) {
    myReference = reference;
    myField = field;
  }

  @NotNull
  @Override
  public ValueDescriptorImpl createValueDescription(@NotNull Project project, @NotNull Value referee) {
    return new FieldDescriptorImpl(project, myReference, myField) {
      @Override
      public Value calcValue(EvaluationContextImpl evaluationContext) {
        return myReference;
      }
    };
  }

  @Nullable
  @Override
  public String getNodeName(int order) {
    return null;
  }

  @NotNull
  @Override
  public ObjectReference getReference() {
    return myReference;
  }

  @NotNull
  @Override
  public Function<XValueNode, XValueNode> getNodeCustomizer() {
    return node -> new XValueNodePresentationConfigurator.ConfigurableXValueNodeImpl() {
      @Override
      public void applyPresentation(@Nullable Icon icon, @NotNull final XValuePresentation valuePresenter, boolean hasChildren) {
        node.setPresentation(icon, new XValuePresentation() {
          @NotNull
          @Override
          public String getSeparator() {
            return " in ";
          }

          @Nullable
          @Override
          public String getType() {
            return valuePresenter.getType();
          }

          @Override
          public void renderValue(@NotNull XValueTextRenderer renderer) {
            valuePresenter.renderValue(renderer);
          }
        }, hasChildren);
      }

      @Override
      public void setFullValueEvaluator(@NotNull XFullValueEvaluator fullValueEvaluator) {
      }
    };
  }
}
