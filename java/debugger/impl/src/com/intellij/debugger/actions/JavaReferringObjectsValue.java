// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.engine.ReferringObject;
import com.intellij.debugger.engine.ReferringObjectsProvider;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.memory.agent.ui.PathsToClosestGcRootsDialog;
import com.intellij.debugger.ui.impl.watch.NodeManagerImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.impl.frame.XValueMarkers;
import com.intellij.xdebugger.impl.ui.tree.actions.ShowReferringObjectsAction;
import com.sun.jdi.ObjectCollectedException;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

public class JavaReferringObjectsValue extends JavaValue implements ShowReferringObjectsAction.ReferrersTreeCustomizer {
  private static final long MAX_REFERRING = 100;
  private final ReferringObjectsProvider myReferringObjectsProvider;
  private final Function<? super XValueNode, ? extends XValueNode> myNodeConfigurator;

  private JavaReferringObjectsValue(@Nullable JavaValue parent,
                                    @NotNull ValueDescriptorImpl valueDescriptor,
                                    @NotNull EvaluationContextImpl evaluationContext,
                                    @NotNull ReferringObjectsProvider referringObjectsProvider,
                                    NodeManagerImpl nodeManager,
                                    @Nullable Function<? super XValueNode, ? extends XValueNode> nodeConfigurator) {
    super(parent, valueDescriptor, evaluationContext, nodeManager, false);
    myReferringObjectsProvider = referringObjectsProvider;
    myNodeConfigurator = nodeConfigurator;
  }

  public JavaReferringObjectsValue(@NotNull JavaValue javaValue,
                                   @NotNull ReferringObjectsProvider referringObjectsProvider,
                                   @Nullable Function<? super XValueNode, ? extends XValueNode> nodeConfigurator) {
    super(null, javaValue.getName(), javaValue.getDescriptor(), javaValue.getEvaluationContext(), javaValue.getNodeManager(), false);
    myReferringObjectsProvider = referringObjectsProvider;
    myNodeConfigurator = nodeConfigurator;
  }

  @Override
  public @Nullable XReferrersProvider getReferrersProvider() {
    return new XReferrersProvider() {
      @Override
      public XValue getReferringObjectsValue() {
        return new JavaReferringObjectsValue(JavaReferringObjectsValue.this, myReferringObjectsProvider, null);
      }
    };
  }

  @Override
  public DialogWrapper getDialog(XDebugSession session, String nodeName, XSourcePosition position, XValueMarkers<?, ?> markers) {
    return new PathsToClosestGcRootsDialog(session.getProject(),
                                           session.getDebugProcess().getEditorsProvider(),
                                           position,
                                           nodeName,
                                           this,
                                           markers, session, false);
  }

  @Override
  public void computeChildren(final @NotNull XCompositeNode node) {
    scheduleCommand(getEvaluationContext(), node, new SuspendContextCommandImpl(getEvaluationContext().getSuspendContext()) {
        @Override
        public Priority getPriority() {
          return Priority.NORMAL;
        }

        @Override
        public void contextAction(@NotNull SuspendContextImpl suspendContext) {
          final XValueChildrenList children = new XValueChildrenList();

          Value value = getDescriptor().getValue();

          List<ReferringObject> referringObjects;
          try {
            referringObjects = myReferringObjectsProvider.getReferringObjects(getEvaluationContext(), (ObjectReference)value,
                                                                              MAX_REFERRING);
          } catch (ObjectCollectedException e) {
            node.setErrorMessage(JavaDebuggerBundle.message("evaluation.error.object.collected"));
            return;
          }
          catch (EvaluateException e) {
            node.setErrorMessage(e.getMessage());
            return;
          }

          int i = 1;
          for (final ReferringObject object : referringObjects) {
            String nodeName = object.getNodeName(i++);
            ValueDescriptorImpl descriptor = object.createValueDescription(getProject(), value);
            JavaReferringObjectsValue referringValue =
              new JavaReferringObjectsValue(null, descriptor, getEvaluationContext(),
                                            myReferringObjectsProvider, getNodeManager(), object.getNodeCustomizer());
            if (nodeName == null) {
              children.add(referringValue);
            }
            else {
              children.add(nodeName, referringValue);
            }
          }

          node.addChildren(children, true);
        }
      }
    );
  }

  @Override
  public void computePresentation(final @NotNull XValueNode node, final @NotNull XValuePlace place) {
    super.computePresentation(myNodeConfigurator == null ? node : myNodeConfigurator.apply(node), place);
  }

  @Override
  public @Nullable XValueModifier getModifier() {
    return null;
  }
}
