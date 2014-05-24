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

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.*;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.settings.ViewsGeneralSettings;
import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.debugger.ui.impl.FrameVariablesTree;
import com.intellij.debugger.ui.impl.watch.*;
import com.intellij.debugger.ui.tree.render.ClassRenderer;
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.sun.jdi.*;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.ExceptionEvent;
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
  private static final Logger LOG = Logger.getInstance(JavaStackFrame.class);

  private final DebugProcessImpl myDebugProcess;
  private final XSourcePosition myXSourcePosition;
  private final SourcePosition mySourcePosition;
  private final NodeManagerImpl myNodeManager;
  private final StackFrameDescriptorImpl myDescriptor;
  private static final JavaFramesListRenderer FRAME_RENDERER = new JavaFramesListRenderer();
  private JavaDebuggerEvaluator myEvaluator = null;

  public JavaStackFrame(@NotNull StackFrameProxyImpl stackFrameProxy, @NotNull DebugProcessImpl debugProcess, MethodsTracker tracker) {
    myDebugProcess = debugProcess;
    Pair<SourcePosition, XSourcePosition> positions = calcSourcePosition(stackFrameProxy);
    mySourcePosition = positions.getFirst();
    myXSourcePosition = positions.getSecond();
    myNodeManager = debugProcess.getXdebugProcess().getNodeManager();
    myDescriptor = new StackFrameDescriptorImpl(stackFrameProxy, tracker);
    myDescriptor.setContext(null);
    myDescriptor.updateRepresentation(null, DescriptorLabelListener.DUMMY_LISTENER);
  }

  public StackFrameDescriptorImpl getDescriptor() {
    return myDescriptor;
  }

  private Pair<SourcePosition, XSourcePosition> calcSourcePosition(StackFrameProxyImpl stackFrameProxy) {
    final CompoundPositionManager positionManager = myDebugProcess.getPositionManager();
    if (positionManager == null) {
      // process already closed
      return null;
    }
    Location location = null;
    try {
      location = stackFrameProxy.location();
    }
    catch (Throwable e) {
      LOG.info(e);
    }
    final Location loc = location;
    return ApplicationManager.getApplication().runReadAction(new Computable<Pair<SourcePosition, XSourcePosition>>() {
      @Override
      public Pair<SourcePosition, XSourcePosition> compute() {
        SourcePosition position = positionManager.getSourcePosition(loc);
        XSourcePosition xSourcePosition = position != null ? DebuggerUtilsEx.toXSourcePosition(position) : null;
        return new Pair<SourcePosition, XSourcePosition>(position, xSourcePosition);
      }
    });
  }

  @Nullable
  @Override
  public XDebuggerEvaluator getEvaluator() {
    if (myEvaluator == null) {
      myEvaluator = new JavaDebuggerEvaluator(myDebugProcess, this);
    }
    return myEvaluator;
  }

  @Nullable
  @Override
  public XSourcePosition getSourcePosition() {
    return myXSourcePosition;
  }

  @Override
  public void customizePresentation(@NotNull ColoredTextContainer component) {
    StackFrameDescriptorImpl selectedDescriptor = null;
    DebuggerSession session = myDebugProcess.getSession();
    if (session != null) {
      XDebugSession xSession = session.getXDebugSession();
      if (xSession != null) {
        XStackFrame frame = xSession.getCurrentStackFrame();
        if (frame instanceof JavaStackFrame) {
          selectedDescriptor = ((JavaStackFrame)frame).getDescriptor();
        }
      }
    }
    FRAME_RENDERER.customizePresentation(myDescriptor, component, selectedDescriptor);
  }

  @Override
  public void computeChildren(@NotNull final XCompositeNode node) {
    XStackFrame xFrame = getDescriptor().getXStackFrame();
    if (xFrame != null) {
      xFrame.computeChildren(node);
      return;
    }
    myDebugProcess.getManagerThread().schedule(new DebuggerContextCommandImpl(myDebugProcess.getDebuggerContext()) {
      @Override
      public void threadAction() {
        XValueChildrenList children = new XValueChildrenList();
        buildVariablesThreadAction(getFrameDebuggerContext(), children, node);
        node.addChildren(children, true);
      }
    });
  }

  DebuggerContextImpl getFrameDebuggerContext() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    DebuggerContextImpl context = myDebugProcess.getDebuggerContext();
    if (context.getFrameProxy() != getStackFrameProxy()) {
      context = DebuggerContextImpl.createDebuggerContext(
        myDebugProcess.mySession,
        context.getSuspendContext(),
        getStackFrameProxy().threadProxy(),
        getStackFrameProxy());
      context.setPositionCache(mySourcePosition);
      context.initCaches();
    }
    return context;
  }

  // copied from DebuggerTree
  private void buildVariablesThreadAction(DebuggerContextImpl debuggerContext, XValueChildrenList children, XCompositeNode node) {
    try {
      final StackFrameDescriptorImpl stackDescriptor = myDescriptor;
      final StackFrameProxyImpl frame = getStackFrameProxy();

      final EvaluationContextImpl evaluationContext = debuggerContext.createEvaluationContext();
      if (!debuggerContext.isEvaluationPossible()) {
        node.setErrorMessage(MessageDescriptor.EVALUATION_NOT_POSSIBLE.getLabel());
        //myChildren.add(myNodeManager.createNode(MessageDescriptor.EVALUATION_NOT_POSSIBLE, evaluationContext));
      }

      final Location location = frame.location();
      LOG.assertTrue(location != null);

      final ObjectReference thisObjectReference = frame.thisObject();
      if (thisObjectReference != null) {
        ValueDescriptorImpl thisDescriptor = myNodeManager.getThisDescriptor(stackDescriptor, thisObjectReference);
        children.add(JavaValue.create(thisDescriptor, evaluationContext, myNodeManager));
      }
      else {
        StaticDescriptorImpl staticDecriptor = myNodeManager.getStaticDescriptor(stackDescriptor, location.method().declaringType());
        if (staticDecriptor.isExpandable()) {
          children.addTopGroup(new JavaStaticGroup(staticDecriptor, evaluationContext, myNodeManager));
        }
      }

      // add last method return value if any
      final Pair<Method, Value> methodValuePair = debuggerContext.getDebugProcess().getLastExecutedMethod();
      if (methodValuePair != null) {
        ValueDescriptorImpl returnValueDescriptor = myNodeManager.getMethodReturnValueDescriptor(stackDescriptor, methodValuePair.getFirst(), methodValuePair.getSecond());
        children.add(JavaValue.create(returnValueDescriptor, evaluationContext, myNodeManager));
      }
      // add context exceptions
      for (Pair<Breakpoint, Event> pair : DebuggerUtilsEx.getEventDescriptors(debuggerContext.getSuspendContext())) {
        final Event debugEvent = pair.getSecond();
        if (debugEvent instanceof ExceptionEvent) {
          final ObjectReference exception = ((ExceptionEvent)debugEvent).exception();
          if (exception != null) {
            final ValueDescriptorImpl exceptionDescriptor = myNodeManager.getThrownExceptionObjectDescriptor(stackDescriptor, exception);
            children.add(JavaValue.create(exceptionDescriptor, evaluationContext, myNodeManager));
          }
        }
      }

      final ClassRenderer classRenderer = NodeRendererSettings.getInstance().getClassRenderer();
      if (classRenderer.SHOW_VAL_FIELDS_AS_LOCAL_VARIABLES) {
        if (thisObjectReference != null && evaluationContext.getDebugProcess().getVirtualMachineProxy().canGetSyntheticAttribute())  {
          final ReferenceType thisRefType = thisObjectReference.referenceType();
          if (thisRefType instanceof ClassType && thisRefType.equals(location.declaringType()) && thisRefType.name().contains("$")) { // makes sense for nested classes only
            final ClassType clsType = (ClassType)thisRefType;
            final DebugProcessImpl debugProcess = debuggerContext.getDebugProcess();
            final VirtualMachineProxyImpl vm = debugProcess.getVirtualMachineProxy();
            for (Field field : clsType.fields()) {
              if ((!vm.canGetSyntheticAttribute() || field.isSynthetic()) && StringUtil
                .startsWith(field.name(), FieldDescriptorImpl.OUTER_LOCAL_VAR_FIELD_PREFIX)) {
                final FieldDescriptorImpl fieldDescriptor = myNodeManager.getFieldDescriptor(stackDescriptor, thisObjectReference, field);
                children.add(JavaValue.create(fieldDescriptor, evaluationContext, myNodeManager));
              }
            }
          }
        }
      }

      try {
        buildVariables(debuggerContext, evaluationContext, children, node);
        //if (classRenderer.SORT_ASCENDING) {
        //  Collections.sort(myChildren, NodeManagerImpl.getNodeComparator());
        //}
      }
      catch (EvaluateException e) {
        node.setErrorMessage(e.getMessage());
        //myChildren.add(myNodeManager.createMessageNode(new MessageDescriptor(e.getMessage())));
      }
    }
    catch (EvaluateException e) {
      node.setErrorMessage(e.getMessage());
      //myChildren.clear();
      //myChildren.add(myNodeManager.createMessageNode(new MessageDescriptor(e.getMessage())));
    }
    catch (InvalidStackFrameException e) {
      LOG.info(e);
      //myChildren.clear();
      //notifyCancelled();
    }
    catch (InternalException e) {
      if (e.errorCode() == 35) {
        node.setErrorMessage(DebuggerBundle.message("error.corrupt.debug.info", e.getMessage()));
        //myChildren.add(
        //  myNodeManager.createMessageNode(new MessageDescriptor(DebuggerBundle.message("error.corrupt.debug.info", e.getMessage()))));
      }
      else {
        throw e;
      }
    }
  }

  // copied from FrameVariablesTree
  private void buildVariables(DebuggerContextImpl debuggerContext, EvaluationContextImpl evaluationContext, XValueChildrenList children, XCompositeNode node) throws EvaluateException {
    boolean myAutoWatchMode = DebuggerSettings.getInstance().AUTO_VARIABLES_MODE;
    if (evaluationContext == null) {
      return;
    }
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
        final Map<String, LocalVariableProxyImpl> visibleVariables = FrameVariablesTree.getVisibleVariables(getStackFrameProxy());
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
            children.add(JavaValue.create(descriptor, evaluationContext, myNodeManager));
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
          children.add(JavaValue.create(descriptor, evaluationContext, myNodeManager));
        }
      }
    }
    catch (EvaluateException e) {
      if (e.getCause() instanceof AbsentInformationException) {
        final StackFrameProxyImpl frame = getStackFrameProxy();

        final Collection<Value> argValues = frame.getArgumentValues();
        int index = 0;
        for (Value argValue : argValues) {
          final ArgumentValueDescriptorImpl descriptor = myNodeManager.getArgumentValueDescriptor(null, index++, argValue, null);
          children.add(JavaValue.create(descriptor, evaluationContext, myNodeManager));
        }
        node.setMessage(MessageDescriptor.LOCAL_VARIABLES_INFO_UNAVAILABLE.getLabel(), XDebuggerUIConstants.INFORMATION_MESSAGE_ICON, SimpleTextAttributes.REGULAR_ATTRIBUTES, null);
        //myChildren.add(myNodeManager.createMessageNode(MessageDescriptor.LOCAL_VARIABLES_INFO_UNAVAILABLE));

        // trying to collect values from variable slots
        final List<DecompiledLocalVariable> decompiled = FrameVariablesTree.collectVariablesFromBytecode(frame, argValues.size());
        if (!decompiled.isEmpty()) {
          try {
            final Map<DecompiledLocalVariable, Value> values = LocalVariablesUtil.fetchValues(frame.getStackFrame(), decompiled);
            for (DecompiledLocalVariable var : decompiled) {
              final Value value = values.get(var);
              final ArgumentValueDescriptorImpl descriptor = myNodeManager.getArgumentValueDescriptor(null, var.getSlot(), value, var.getName());
              children.add(JavaValue.create(descriptor, evaluationContext, myNodeManager));
            }
          }
          catch (Exception ex) {
            LOG.info(ex);
          }
        }
      }
      else {
        throw e;
      }
    }
  }

  protected void superBuildVariables(final EvaluationContextImpl evaluationContext, XValueChildrenList children) throws EvaluateException {
    final StackFrameProxyImpl frame = getStackFrameProxy();
    for (final LocalVariableProxyImpl local : frame.visibleVariables()) {
      final LocalVariableDescriptorImpl descriptor = myNodeManager.getLocalVariableDescriptor(null, local);
      children.add(JavaValue.create(descriptor, evaluationContext, myNodeManager));
    }
  }

  public StackFrameProxyImpl getStackFrameProxy() {
    return myDescriptor.getFrameProxy();
  }

  @Nullable
  @Override
  public Object getEqualityObject() {
    return getStackFrameProxy().hashCode();
  }
}
