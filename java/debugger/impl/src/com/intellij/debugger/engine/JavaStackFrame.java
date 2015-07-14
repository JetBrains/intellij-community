/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.debugger.ui.impl.FrameVariablesTree;
import com.intellij.debugger.ui.impl.watch.*;
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.intellij.xdebugger.settings.XDebuggerSettingsManager;
import com.sun.jdi.*;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.ExceptionEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author egor
 */
public class JavaStackFrame extends XStackFrame {
  private static final Logger LOG = Logger.getInstance(JavaStackFrame.class);

  private final DebugProcessImpl myDebugProcess;
  @Nullable private final XSourcePosition myXSourcePosition;
  private final NodeManagerImpl myNodeManager;
  @NotNull private final StackFrameDescriptorImpl myDescriptor;
  private static final JavaFramesListRenderer FRAME_RENDERER = new JavaFramesListRenderer();
  private JavaDebuggerEvaluator myEvaluator = null;
  private final String myEqualityObject;

  public JavaStackFrame(@NotNull StackFrameProxyImpl stackFrameProxy,
                        @NotNull MethodsTracker tracker) {
    this(new StackFrameDescriptorImpl(stackFrameProxy, tracker), true);
  }

  public JavaStackFrame(@NotNull StackFrameDescriptorImpl descriptor, boolean update) {
    myDescriptor = descriptor;
    if (update) {
      myDescriptor.setContext(null);
      myDescriptor.updateRepresentation(null, DescriptorLabelListener.DUMMY_LISTENER);
    }
    myEqualityObject = update ? NodeManagerImpl.getContextKeyForFrame(myDescriptor.getFrameProxy()) : null;
    myDebugProcess = ((DebugProcessImpl)descriptor.getDebugProcess());
    myNodeManager = myDebugProcess.getXdebugProcess().getNodeManager();
    myXSourcePosition = myDescriptor.getSourcePosition() != null ? DebuggerUtilsEx.toXSourcePosition(myDescriptor.getSourcePosition()) : null;
  }

  @NotNull
  public StackFrameDescriptorImpl getDescriptor() {
    return myDescriptor;
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
      public Priority getPriority() {
        return Priority.NORMAL;
      }

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
      SuspendContextImpl threadSuspendContext = SuspendManagerUtil.getSuspendContextForThread(context.getSuspendContext(),
                                                                                              getStackFrameProxy().threadProxy());
      context = DebuggerContextImpl.createDebuggerContext(
        myDebugProcess.mySession,
        threadSuspendContext,
        getStackFrameProxy().threadProxy(),
        getStackFrameProxy());
      context.setPositionCache(myDescriptor.getSourcePosition());
      context.initCaches();
    }
    return context;
  }

  // copied from DebuggerTree
  private void buildVariablesThreadAction(DebuggerContextImpl debuggerContext, XValueChildrenList children, XCompositeNode node) {
    try {
      final EvaluationContextImpl evaluationContext = debuggerContext.createEvaluationContext();
      if (evaluationContext == null) {
        return;
      }
      if (!debuggerContext.isEvaluationPossible()) {
        node.setErrorMessage(MessageDescriptor.EVALUATION_NOT_POSSIBLE.getLabel());
        //myChildren.add(myNodeManager.createNode(MessageDescriptor.EVALUATION_NOT_POSSIBLE, evaluationContext));
      }

      final Location location = myDescriptor.getLocation();

      final ObjectReference thisObjectReference = myDescriptor.getThisObject();
      if (thisObjectReference != null) {
        ValueDescriptorImpl thisDescriptor = myNodeManager.getThisDescriptor(null, thisObjectReference);
        children.add(JavaValue.create(thisDescriptor, evaluationContext, myNodeManager));
      }
      else if (location != null) {
        StaticDescriptorImpl staticDecriptor = myNodeManager.getStaticDescriptor(myDescriptor, location.declaringType());
        if (staticDecriptor.isExpandable()) {
          children.addTopGroup(new JavaStaticGroup(staticDecriptor, evaluationContext, myNodeManager));
        }
      }

      DebugProcessImpl debugProcess = debuggerContext.getDebugProcess();
      if (debugProcess == null) {
        return;
      }

      // add last method return value if any
      final Pair<Method, Value> methodValuePair = debugProcess.getLastExecutedMethod();
      if (methodValuePair != null) {
        ValueDescriptorImpl returnValueDescriptor = myNodeManager.getMethodReturnValueDescriptor(myDescriptor, methodValuePair.getFirst(), methodValuePair.getSecond());
        children.add(JavaValue.create(returnValueDescriptor, evaluationContext, myNodeManager));
      }
      // add context exceptions
      for (Pair<Breakpoint, Event> pair : DebuggerUtilsEx.getEventDescriptors(debuggerContext.getSuspendContext())) {
        final Event debugEvent = pair.getSecond();
        if (debugEvent instanceof ExceptionEvent) {
          final ObjectReference exception = ((ExceptionEvent)debugEvent).exception();
          if (exception != null) {
            final ValueDescriptorImpl exceptionDescriptor = myNodeManager.getThrownExceptionObjectDescriptor(myDescriptor, exception);
            children.add(JavaValue.create(exceptionDescriptor, evaluationContext, myNodeManager));
          }
        }
      }

      try {
        buildVariables(debuggerContext, evaluationContext, debugProcess, children, thisObjectReference, location);
        //if (classRenderer.SORT_ASCENDING) {
        //  Collections.sort(myChildren, NodeManagerImpl.getNodeComparator());
        //}
      }
      catch (EvaluateException e) {
        node.setErrorMessage(e.getMessage());
        //myChildren.add(myNodeManager.createMessageNode(new MessageDescriptor(e.getMessage())));
      }
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
  private void buildVariables(DebuggerContextImpl debuggerContext,
                              EvaluationContextImpl evaluationContext,
                              @NotNull DebugProcessImpl debugProcess,
                              XValueChildrenList children,
                              ObjectReference thisObjectReference,
                              Location location) throws EvaluateException {
    final Set<String> visibleLocals = new HashSet<String>();
    if (NodeRendererSettings.getInstance().getClassRenderer().SHOW_VAL_FIELDS_AS_LOCAL_VARIABLES) {
      if (thisObjectReference != null && debugProcess.getVirtualMachineProxy().canGetSyntheticAttribute()) {
        final ReferenceType thisRefType = thisObjectReference.referenceType();
        if (thisRefType instanceof ClassType && location != null
            && thisRefType.equals(location.declaringType()) && thisRefType.name().contains("$")) { // makes sense for nested classes only
          for (Field field : thisRefType.fields()) {
            if (DebuggerUtils.isSynthetic(field) && StringUtil.startsWith(field.name(), FieldDescriptorImpl.OUTER_LOCAL_VAR_FIELD_PREFIX)) {
              final FieldDescriptorImpl fieldDescriptor = myNodeManager.getFieldDescriptor(myDescriptor, thisObjectReference, field);
              children.add(JavaValue.create(fieldDescriptor, evaluationContext, myNodeManager));
              visibleLocals.add(fieldDescriptor.getName());
            }
          }
        }
      }
    }

    boolean myAutoWatchMode = DebuggerSettings.getInstance().AUTO_VARIABLES_MODE;
    if (evaluationContext == null) {
      return;
    }
    final SourcePosition sourcePosition = debuggerContext.getSourcePosition();
    if (sourcePosition == null) {
      return;
    }

    try {
      if (!XDebuggerSettingsManager.getInstance().getDataViewSettings().isAutoExpressions() && !myAutoWatchMode) {
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
              return FrameVariablesTree.findReferencedVars(ContainerUtil.union(visibleVariables.keySet(), visibleLocals), sourcePosition, evalContext);
            }
          });
        // add locals
        if (myAutoWatchMode) {
          for (String var : usedVars.first) {
            LocalVariableProxyImpl local = visibleVariables.get(var);
            if (local != null) {
              children.add(JavaValue.create(myNodeManager.getLocalVariableDescriptor(null, local), evaluationContext, myNodeManager));
            }
          }
        }
        else {
          superBuildVariables(evaluationContext, children);
        }
        final EvaluationContextImpl evalContextCopy = evaluationContext.createEvaluationContext(evaluationContext.getThisObject());
        evalContextCopy.setAutoLoadClasses(false);

        final Set<TextWithImports> extraVars = computeExtraVars(usedVars, sourcePosition, evalContext);

        // add extra vars
        addToChildrenFrom(extraVars, children, evaluationContext);

        // add expressions
        addToChildrenFrom(usedVars.second, children, evalContextCopy);
      }
    }
    catch (EvaluateException e) {
      if (e.getCause() instanceof AbsentInformationException) {
        final StackFrameProxyImpl frame = getStackFrameProxy();

        final Collection<Value> argValues = frame.getArgumentValues();
        int index = 0;
        for (Value argValue : argValues) {
          children.add(createArgumentValue(index++, argValue, null, evaluationContext));
        }
        //node.setMessage(MessageDescriptor.LOCAL_VARIABLES_INFO_UNAVAILABLE.getLabel(), XDebuggerUIConstants.INFORMATION_MESSAGE_ICON, SimpleTextAttributes.REGULAR_ATTRIBUTES, null);
        children.add(new DummyMessageValueNode(MessageDescriptor.LOCAL_VARIABLES_INFO_UNAVAILABLE.getLabel(), XDebuggerUIConstants.INFORMATION_MESSAGE_ICON));
        //myChildren.add(myNodeManager.createMessageNode(MessageDescriptor.LOCAL_VARIABLES_INFO_UNAVAILABLE));

        // trying to collect values from variable slots
        final List<DecompiledLocalVariable> decompiled = FrameVariablesTree.collectVariablesFromBytecode(frame, argValues.size());
        if (!decompiled.isEmpty()) {
          try {
            final Map<DecompiledLocalVariable, Value> values = LocalVariablesUtil.fetchValues(frame.getStackFrame(), decompiled);
            for (DecompiledLocalVariable var : decompiled) {
              children.add(createArgumentValue(var.getSlot(), values.get(var), var.getName(), evaluationContext));
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

  private static Set<TextWithImports> computeExtraVars(Pair<Set<String>, Set<TextWithImports>> usedVars,
                                                       SourcePosition sourcePosition,
                                                       EvaluationContextImpl evalContext) {
    Set<String> alreadyCollected = new HashSet<String>(usedVars.first);
    for (TextWithImports text : usedVars.second) {
      alreadyCollected.add(text.getText());
    }
    Set<TextWithImports> extra = new HashSet<TextWithImports>();
    for (FrameExtraVariablesProvider provider : FrameExtraVariablesProvider.EP_NAME.getExtensions()) {
      if (provider.isAvailable(sourcePosition, evalContext)) {
        extra.addAll(provider.collectVariables(sourcePosition, evalContext, alreadyCollected));
      }
    }
    return extra;
  }

  private void addToChildrenFrom(Set<TextWithImports> expressions, XValueChildrenList children, EvaluationContextImpl evaluationContext) {
    for (TextWithImports text : expressions) {
      WatchItemDescriptor descriptor = myNodeManager.getWatchItemDescriptor(null, text, null);
      children.add(JavaValue.create(descriptor, evaluationContext, myNodeManager));
    }
  }

  static class DummyMessageValueNode extends XNamedValue {
    private final String myMessage;
    private final Icon myIcon;

    public DummyMessageValueNode(String message, Icon icon) {
      super("");
      myMessage = message;
      myIcon = icon;
    }

    @Override
    public void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
      node.setPresentation(myIcon, new XValuePresentation() {
        @NotNull
        @Override
        public String getSeparator() {
          return "";
        }

        @Override
        public void renderValue(@NotNull XValueTextRenderer renderer) {
          renderer.renderValue(myMessage);
        }
      }, false);
    }
  }

  private JavaValue createArgumentValue(int index, Value value, String name, EvaluationContextImpl evaluationContext) {
    ArgumentValueDescriptorImpl descriptor = myNodeManager.getArgumentValueDescriptor(null, index, value, name);
    // setContext is required to calculate correct name
    descriptor.setContext(evaluationContext);
    return JavaValue.create(null, descriptor, evaluationContext, myNodeManager, true);
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
    return myEqualityObject;
  }

  @Override
  public String toString() {
    if (myXSourcePosition != null) {
      return "JavaFrame " + myXSourcePosition.getFile().getName() + ":" + myXSourcePosition.getLine();
    }
    else {
      return "JavaFrame position unknown";
    }
  }
}
