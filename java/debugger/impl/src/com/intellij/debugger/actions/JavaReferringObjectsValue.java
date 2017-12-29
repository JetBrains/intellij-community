/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.ui.impl.watch.FieldDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.NodeManagerImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.psi.PsiExpression;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodePresentationConfigurator;
import com.sun.jdi.Field;
import com.sun.jdi.ObjectCollectedException;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class JavaReferringObjectsValue extends JavaValue {
  private static final long MAX_REFERRING = 100;
  private final boolean myIsField;

  private JavaReferringObjectsValue(@Nullable JavaValue parent,
                                    @NotNull ValueDescriptorImpl valueDescriptor,
                                    @NotNull EvaluationContextImpl evaluationContext,
                                    NodeManagerImpl nodeManager,
                                    boolean isField) {
    super(parent, valueDescriptor, evaluationContext, nodeManager, false);
    myIsField = isField;
  }

  public JavaReferringObjectsValue(@NotNull JavaValue javaValue, boolean isField) {
    super(null, javaValue.getDescriptor(), javaValue.getEvaluationContext(), javaValue.getNodeManager(), false);
    myIsField = isField;
  }

  @Override
  public boolean canNavigateToSource() {
    return true;
  }

  @Override
  public void computeChildren(@NotNull final XCompositeNode node) {
    scheduleCommand(getEvaluationContext(), node, new SuspendContextCommandImpl(getEvaluationContext().getSuspendContext()) {
        @Override
        public Priority getPriority() {
          return Priority.NORMAL;
        }

        @Override
        public void contextAction(@NotNull SuspendContextImpl suspendContext) throws Exception {
          final XValueChildrenList children = new XValueChildrenList();

          Value value = getDescriptor().getValue();

          List<ObjectReference> references;
          try {
            references = ((ObjectReference)value).referringObjects(MAX_REFERRING);
          } catch (ObjectCollectedException e) {
            node.setErrorMessage(DebuggerBundle.message("evaluation.error.object.collected"));
            return;
          }

          int i = 1;
          for (final ObjectReference reference : references) {
            // try to find field name
            Field field = findField(reference, value);
            if (field != null) {
              ValueDescriptorImpl descriptor = new FieldDescriptorImpl(getProject(), reference, field) {
                @Override
                public Value calcValue(EvaluationContextImpl evaluationContext) throws EvaluateException {
                  return reference;
                }
              };
              children.add(new JavaReferringObjectsValue(null, descriptor, getEvaluationContext(), getNodeManager(), true));
              i++;
            }
            else {
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
                public PsiExpression getDescriptorEvaluation(DebuggerContext context) throws EvaluateException {
                  return null;
                }
              };
              children.add("Referrer " + i++, new JavaReferringObjectsValue(null, descriptor, getEvaluationContext(), getNodeManager(), false));
            }
          }

          node.addChildren(children, true);
        }
      }
    );
  }

  @Override
  public void computePresentation(@NotNull final XValueNode node, @NotNull final XValuePlace place) {
    if (!myIsField) {
      super.computePresentation(node, place);
    }
    else {
      super.computePresentation(new XValueNodePresentationConfigurator.ConfigurableXValueNodeImpl() {
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
      }, place);
    }
  }

  private static Field findField(ObjectReference reference, Value value) {
    for (Field field : reference.referenceType().allFields()) {
      if (reference.getValue(field) == value) {
        return field;
      }
    }
    return null;
  }

  @Nullable
  @Override
  public XValueModifier getModifier() {
    return null;
  }
}
