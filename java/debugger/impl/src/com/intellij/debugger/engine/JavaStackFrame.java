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

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.DecompiledLocalVariable;
import com.intellij.debugger.jdi.LocalVariableProxyImpl;
import com.intellij.debugger.jdi.LocalVariablesUtil;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.settings.ViewsGeneralSettings;
import com.intellij.debugger.ui.impl.FrameVariablesTree;
import com.intellij.debugger.ui.impl.watch.*;
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Location;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author egor
 */
public class JavaStackFrame extends XStackFrame {
  private final StackFrameProxyImpl myStackFrameProxy;
  private final DebugProcessImpl myDebugProcess;
  private final XSourcePosition mySourcePosition;
  private final NodeDescriptorFactoryImpl myNodeManager;
  private final StackFrameDescriptorImpl myDescriptor;
  private final JavaFramesListRenderer myRenderer = new JavaFramesListRenderer();

  public JavaStackFrame(@NotNull StackFrameProxyImpl stackFrameProxy, @NotNull DebugProcessImpl debugProcess, MethodsTracker tracker) {
    myStackFrameProxy = stackFrameProxy;
    myDebugProcess = debugProcess;
    mySourcePosition = calcSourcePosition();
    myNodeManager = new NodeDescriptorFactoryImpl(myDebugProcess.getProject());
    myDescriptor = new StackFrameDescriptorImpl(stackFrameProxy, tracker);
    myDescriptor.setContext(null);
    myDescriptor.updateRepresentation(null, DescriptorLabelListener.DUMMY_LISTENER);
  }

  private XSourcePosition calcSourcePosition() {
    final CompoundPositionManager positionManager = myDebugProcess.getPositionManager();
    if (positionManager == null) {
      // process already closed
      return null;
    }
    Location location = null;
    try {
      location = myStackFrameProxy.location();
    }
    catch (Throwable e) {
      //TODO: handle
    }
    final Location loc = location;
    return ApplicationManager.getApplication().runReadAction(new Computable<XSourcePosition>() {
      @Override
      public XSourcePosition compute() {
        SourcePosition position = positionManager.getSourcePosition(loc);
        if (position != null) {
          return DebuggerUtilsEx.toXSourcePosition(position);
        }
        return null;
      }
    });
  }

  @Nullable
  @Override
  public XDebuggerEvaluator getEvaluator() {
    DebuggerContextImpl context = DebuggerManagerEx.getInstanceEx(myDebugProcess.getProject()).getContext();
    return new JavaDebuggerEvaluator(myDebugProcess, context);
  }

  @Nullable
  @Override
  public XSourcePosition getSourcePosition() {
    return mySourcePosition;
  }

  @Override
  public void customizePresentation(@NotNull ColoredTextContainer component) {
    myRenderer.customizePresentation(myDescriptor, component);
  }

  @Override
  public void computeChildren(@NotNull final XCompositeNode node) {
    myDebugProcess.getManagerThread().schedule(new DebuggerCommandImpl() {
      @Override
      protected void action() throws Exception {
        XValueChildrenList children = new XValueChildrenList();
        DebuggerContextImpl debuggerContext = DebuggerManagerEx.getInstanceEx(myDebugProcess.getProject()).getContext();
        try {
          buildVariables(debuggerContext, children);
        }
        catch (EvaluateException e) {
          e.printStackTrace();
        }
        node.addChildren(children, true);
      }
    });
  }

  private static boolean myAutoWatchMode = false;

  // copied from FrameVariablesTree
  private void buildVariables(DebuggerContextImpl debuggerContext, XValueChildrenList children) throws EvaluateException {
    EvaluationContextImpl evaluationContext = debuggerContext.createEvaluationContext();
    final SourcePosition sourcePosition = debuggerContext.getSourcePosition();
    if (sourcePosition == null) {
      return;
    }

    try {
      if (!ViewsGeneralSettings.getInstance().ENABLE_AUTO_EXPRESSIONS && !myAutoWatchMode) {
        // optimization
        superBuildVariables(evaluationContext, children);
      }
      else {
        final Map<String, LocalVariableProxyImpl> visibleVariables = FrameVariablesTree.getVisibleVariables(myStackFrameProxy);
        final EvaluationContextImpl evalContext = evaluationContext;
        final Pair<Set<String>, Set<TextWithImports>> usedVars =
          ApplicationManager.getApplication().runReadAction(new Computable<Pair<Set<String>, Set<TextWithImports>>>() {
            @Override
            public Pair<Set<String>, Set<TextWithImports>> compute() {
              return FrameVariablesTree.findReferencedVars(visibleVariables.keySet(), sourcePosition, evalContext);
            }
          });
        // add locals
        if (myAutoWatchMode) {
          for (String var : usedVars.first) {
            final LocalVariableDescriptorImpl descriptor = myNodeManager.getLocalVariableDescriptor(null, visibleVariables.get(var));
            children.add(new JavaValue(descriptor, evaluationContext));
            //myChildren.add(myNodeManager.createNode(descriptor, evaluationContext));
          }
        }
        else {
          superBuildVariables(evaluationContext, children);
        }
        // add expressions
        final EvaluationContextImpl evalContextCopy = evaluationContext.createEvaluationContext(evaluationContext.getThisObject());
        evalContextCopy.setAutoLoadClasses(false);
        for (TextWithImports text : usedVars.second) {
          WatchItemDescriptor descriptor = myNodeManager.getWatchItemDescriptor(null, text, null);
          children.add(new JavaValue(descriptor, evaluationContext));
          //myChildren.add(myNodeManager.createNode(descriptor, evalContextCopy));
        }
      }
    }
    catch (EvaluateException e) {
      if (e.getCause() instanceof AbsentInformationException) {
        final StackFrameProxyImpl frame = myStackFrameProxy;

        final Collection<Value> argValues = frame.getArgumentValues();
        int index = 0;
        for (Value argValue : argValues) {
          final ArgumentValueDescriptorImpl descriptor = myNodeManager.getArgumentValueDescriptor(null, index++, argValue, null);
          children.add(new JavaValue(descriptor, evaluationContext));
          //final DebuggerTreeNodeImpl variableNode = myNodeManager.createNode(descriptor, evaluationContext);
          //myChildren.add(variableNode);
        }
        //myChildren.add(myNodeManager.createMessageNode(MessageDescriptor.LOCAL_VARIABLES_INFO_UNAVAILABLE));
        // trying to collect values from variable slots
        final List<DecompiledLocalVariable> decompiled = FrameVariablesTree.collectVariablesFromBytecode(frame, argValues.size());
        if (!decompiled.isEmpty()) {
          try {
            final Map<DecompiledLocalVariable, Value> values = LocalVariablesUtil.fetchValues(frame.getStackFrame(), decompiled);
            for (DecompiledLocalVariable var : decompiled) {
              final Value value = values.get(var);
              final ArgumentValueDescriptorImpl descriptor = myNodeManager.getArgumentValueDescriptor(null, var.getSlot(), value, var.getName());
              children.add(new JavaValue(descriptor, evaluationContext));
              //final DebuggerTreeNodeImpl variableNode = myNodeManager.createNode(descriptor, evaluationContext);
              //myChildren.add(variableNode);
            }
          }
          catch (Exception ex) {
            //LOG.info(ex);
          }
        }
      }
      else {
        throw e;
      }
    }
  }

  protected void superBuildVariables(final EvaluationContextImpl evaluationContext, XValueChildrenList children) throws EvaluateException {
    final StackFrameProxyImpl frame = myStackFrameProxy;
    for (final LocalVariableProxyImpl local : frame.visibleVariables()) {
      final LocalVariableDescriptorImpl descriptor = myNodeManager.getLocalVariableDescriptor(null, local);
      children.add(new JavaValue(descriptor, evaluationContext));
      //final DebuggerTreeNodeImpl variableNode = myNodeManager.createNode(descriptor, evaluationContext);
      //myChildren.add(variableNode);
    }
  }
}
