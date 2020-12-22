// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent;

import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.engine.ReferringObject;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiExpression;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.frame.XFullValueEvaluator;
import com.intellij.xdebugger.frame.XValueNode;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodePresentationConfigurator;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CompoundRootReferringObject implements ReferringObject {
  private final MemoryAgentReferenceKind[] myKinds;

  public CompoundRootReferringObject(MemoryAgentReferenceKind @NotNull[] kinds) {
    myKinds = ContainerUtil.set(kinds).toArray(new MemoryAgentReferenceKind[0]);
  }

  @NotNull
  @Override
  public ValueDescriptorImpl createValueDescription(@NotNull Project project, @NotNull Value referee) {
    return new ValueDescriptorImpl(project, null) {
      @Override
      public String getName() {
        return "";
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

  @NotNull
  @Override
  public final Function<XValueNode, XValueNode> getNodeCustomizer() {
    return node -> new XValueNodePresentationConfigurator.ConfigurableXValueNodeImpl() {
      @Override
      public void applyPresentation(@Nullable Icon icon, @NotNull final XValuePresentation valuePresenter, boolean hasChildren) {
        node.setPresentation(AllIcons.Nodes.Record, new XValuePresentation() {
          @NotNull
          @Override
          public String getSeparator() {
            return ": ";
          }

          @Nullable
          @Override
          public String getType() {
            return null;
          }

          @Override
          public void renderValue(@NotNull XValueTextRenderer renderer) {
            renderer.renderValue(Arrays.stream(myKinds).map(kind -> kind.toString() + " reference").collect(Collectors.joining(", ")));
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
  public String getNodeName(int order) {
    return "Root";
  }

  @Nullable
  @Override
  public ObjectReference getReference() {
    return null;
  }
}
