/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

/*
 * Class Breakpoint
 * @author Jeka
 */
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.*;
import com.intellij.debugger.engine.*;
import com.intellij.debugger.engine.evaluation.*;
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilderImpl;
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.debugger.engine.evaluation.expression.UnBoxingEvaluator;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.requests.ClassPrepareRequestor;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.ThreeState;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.breakpoints.SuspendPolicy;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.XDebuggerHistoryManager;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import com.intellij.xdebugger.impl.breakpoints.ui.XBreakpointActionsPanel;
import com.sun.jdi.*;
import com.sun.jdi.event.LocatableEvent;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.debugger.breakpoints.properties.JavaBreakpointProperties;

import javax.swing.*;
import java.util.List;

public abstract class Breakpoint<P extends JavaBreakpointProperties> implements FilteredRequestor, ClassPrepareRequestor {
  public static final Key<Breakpoint> DATA_KEY = Key.create("JavaBreakpoint");

  final XBreakpoint<P> myXBreakpoint;
  protected final Project myProject;

  @NonNls private static final String LOG_MESSAGE_OPTION_NAME = "LOG_MESSAGE";
  public static final Breakpoint[] EMPTY_ARRAY = new Breakpoint[0];
  protected boolean myCachedVerifiedState = false;

  protected Breakpoint(@NotNull Project project, XBreakpoint<P> xBreakpoint) {
    myProject = project;
    myXBreakpoint = xBreakpoint;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  protected P getProperties() {
    return myXBreakpoint.getProperties();
  }

  public XBreakpoint<P> getXBreakpoint() {
    return myXBreakpoint;
  }

  @Nullable
  public abstract PsiClass getPsiClass();
  /**
   * Request for creating all needed JPDA requests in the specified VM
   * @param debuggerProcess the requesting process
   */
  public abstract void createRequest(DebugProcessImpl debugProcess);

  protected boolean shouldCreateRequest(final DebugProcessImpl debugProcess) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        JavaDebugProcess process = debugProcess.getXdebugProcess();
        return process != null
               && debugProcess.isAttached()
               && ((XDebugSessionImpl)process.getSession()).isBreakpointActive(myXBreakpoint)
               && debugProcess.getRequestsManager().findRequests(Breakpoint.this).isEmpty();
      }
    });
  }

  /**
   * Request for creating all needed JPDA requests in the specified VM
   * @param debuggerProcess the requesting process
   */
  @Override
  public abstract void processClassPrepare(DebugProcess debuggerProcess, final ReferenceType referenceType);

  public abstract String getDisplayName ();
  
  public String getShortName() {
    return getDisplayName();
  }

  @Nullable
  public String getClassName() {
    return null;
  }

  public void markVerified(boolean isVerified) {
    myCachedVerifiedState = isVerified;
  }

  public boolean isRemoveAfterHit() {
    return myXBreakpoint instanceof XLineBreakpoint && ((XLineBreakpoint)myXBreakpoint).isTemporary();
  }

  public void setRemoveAfterHit(boolean value) {
    if (myXBreakpoint instanceof XLineBreakpoint) {
      ((XLineBreakpoint)myXBreakpoint).setTemporary(value);
    }
  }

  @Nullable
  public String getShortClassName() {
    final String className = getClassName();
    if (className == null) {
      return null;
    }

    final int dotIndex = className.lastIndexOf('.');
    return dotIndex >= 0 && dotIndex + 1 < className.length() ? className.substring(dotIndex + 1) : className;
  }

  @Nullable
  public String getPackageName() {
    return null;
  }

  public abstract Icon getIcon();

  public abstract void reload();

  /**
   * returns UI representation
   */
  public abstract String getEventMessage(LocatableEvent event);

  public abstract boolean isValid();

  public abstract Key<? extends Breakpoint> getCategory();

  /**
   * Associates breakpoint with class.
   *    Create requests for loaded class and registers callback for loading classes
   * @param debugProcess the requesting process
   */
  protected void createOrWaitPrepare(DebugProcessImpl debugProcess, String classToBeLoaded) {
    debugProcess.getRequestsManager().callbackOnPrepareClasses(this, classToBeLoaded);

    List list = debugProcess.getVirtualMachineProxy().classesByName(classToBeLoaded);
    for (final Object aList : list) {
      ReferenceType refType = (ReferenceType)aList;
      if (refType.isPrepared()) {
        processClassPrepare(debugProcess, refType);
      }
    }
  }

  protected void createOrWaitPrepare(final DebugProcessImpl debugProcess, @NotNull final SourcePosition classPosition) {
    debugProcess.getRequestsManager().callbackOnPrepareClasses(this, classPosition);

    debugProcess.getPositionManager().getAllClasses(classPosition).stream()
      .filter(ReferenceType::isPrepared)
      .forEach(refType -> processClassPrepare(debugProcess, refType));
  }

  protected ObjectReference getThisObject(SuspendContextImpl context, LocatableEvent event) throws EvaluateException {
    ThreadReferenceProxyImpl thread = context.getThread();
    if(thread != null) {
      StackFrameProxyImpl stackFrameProxy = thread.frame(0);
      if(stackFrameProxy != null) {
        return stackFrameProxy.thisObject();
      }
    }
    return null;
  }

  @Override
  public boolean processLocatableEvent(final SuspendContextCommandImpl action, final LocatableEvent event) throws EventProcessingException {
    final SuspendContextImpl context = action.getSuspendContext();
    if(!isValid()) {
      context.getDebugProcess().getRequestsManager().deleteRequest(this);
      return false;
    }

    final String[] title = {DebuggerBundle.message("title.error.evaluating.breakpoint.condition") };

    try {
      final StackFrameProxyImpl frameProxy = context.getThread().frame(0);
      if (frameProxy == null) {
        // might be if the thread has been collected
        return false;
      }

      final EvaluationContextImpl evaluationContext = new EvaluationContextImpl(
        action.getSuspendContext(),
        frameProxy,
        getThisObject(context, event)
      );

      if(!evaluateCondition(evaluationContext, event)) {
        return false;
      }

      title[0] = DebuggerBundle.message("title.error.evaluating.breakpoint.action");
      runAction(evaluationContext, event);
    }
    catch (final EvaluateException ex) {
      if(ApplicationManager.getApplication().isUnitTestMode()) {
        System.out.println(ex.getMessage());
        return false;
      }

      throw new EventProcessingException(title[0], ex.getMessage(), ex);
    } 

    return true;
  }

  private void runAction(final EvaluationContextImpl context, LocatableEvent event) {
    final DebugProcessImpl debugProcess = context.getDebugProcess();
    if (isLogEnabled() || isLogExpressionEnabled()) {
      final StringBuilder buf = StringBuilderSpinAllocator.alloc();
      try {
        if (myXBreakpoint.isLogMessage()) {
          buf.append(getEventMessage(event));
          buf.append("\n");
        }
        if (isLogExpressionEnabled()) {
          if(!debugProcess.isAttached()) {
            return;
          }

          final TextWithImports expressionToEvaluate = getLogMessage();
          try {
            ExpressionEvaluator evaluator = DebuggerInvocationUtil.commitAndRunReadAction(myProject, new EvaluatingComputable<ExpressionEvaluator>() {
              @Override
              public ExpressionEvaluator compute() throws EvaluateException {
                return EvaluatorBuilderImpl.build(expressionToEvaluate,
                                                  ContextUtil.getContextElement(context),
                                                  ContextUtil.getSourcePosition(context),
                                                  myProject);
              }
            });
            final Value eval = evaluator.evaluate(context);
            final String result = eval instanceof VoidValue ? "void" : DebuggerUtils.getValueAsString(context, eval);
            buf.append(result);
          }
          catch (EvaluateException e) {
            buf.append(DebuggerBundle.message("error.unable.to.evaluate.expression"));
            buf.append(" \"");
            buf.append(expressionToEvaluate);
            buf.append("\"");
            buf.append(" : ");
            buf.append(e.getMessage());
          }
          buf.append("\n");
        }
        if (buf.length() > 0) {
          debugProcess.printToConsole(buf.toString());
        }
      }
      finally {
        StringBuilderSpinAllocator.dispose(buf);
      }
    }
    if (isRemoveAfterHit()) {
      handleTemporaryBreakpointHit(debugProcess);
    }
  }

  /**
   * @return true if the ID was added or false otherwise
   */
  private boolean hasObjectID(long id) {
    for (InstanceFilter instanceFilter : getInstanceFilters()) {
      if (instanceFilter.getId() == id) {
        return true;
      }
    }
    return false;
  }

  public boolean evaluateCondition(final EvaluationContextImpl context, LocatableEvent event) throws EvaluateException {
    final DebugProcessImpl debugProcess = context.getDebugProcess();
    if (isCountFilterEnabled()) {
      debugProcess.getVirtualMachineProxy().suspend();
      debugProcess.getRequestsManager().deleteRequest(this);
      ((Breakpoint)this).createRequest(debugProcess);
      debugProcess.getVirtualMachineProxy().resume();
    }
    if (isInstanceFiltersEnabled()) {
      Value value = context.getThisObject();
      if (value != null) {  // non-static
        ObjectReference reference = (ObjectReference)value;
        if (!hasObjectID(reference.uniqueID())) {
          return false;
        }
      }
    }

    if (isClassFiltersEnabled()) {
      String typeName = calculateEventClass(context, event);
      if (!typeMatchesClassFilters(typeName)) return false;
    }

    if (!isConditionEnabled() || getCondition().getText().isEmpty()) {
      return true;
    }

    StackFrameProxyImpl frame = context.getFrameProxy();
    if (frame != null) {
      Location location = frame.location();
      if (location != null) {
        ThreeState result = debugProcess.getPositionManager().evaluateCondition(context, frame, location, getCondition().getText());
        if (result != ThreeState.UNSURE) {
          return result == ThreeState.YES;
        }
      }
    }

    try {
      final Project project = context.getProject();
      ExpressionEvaluator evaluator = DebuggerInvocationUtil.commitAndRunReadAction(project, new EvaluatingComputable<ExpressionEvaluator>() {
        @Override
        public ExpressionEvaluator compute() throws EvaluateException {
          final SourcePosition contextSourcePosition = ContextUtil.getSourcePosition(context);
          // IMPORTANT: calculate context psi element basing on the location where the exception
          // has been hit, not on the location where it was set. (For line breakpoints these locations are the same, however,
          // for method, exception and field breakpoints these locations differ)
          PsiElement contextPsiElement = ContextUtil.getContextElement(contextSourcePosition);
          if (contextPsiElement == null) {
            contextPsiElement = getEvaluationElement(); // as a last resort
          }
          return EvaluatorBuilderImpl.build(getCondition(), contextPsiElement, contextSourcePosition, project);
        }
      });
      Object value = UnBoxingEvaluator.unbox(evaluator.evaluate(context), context);
      if (!(value instanceof BooleanValue)) {
        throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.boolean.expected"));
      }
      if (!((BooleanValue)value).booleanValue()) {
        return false;
      }
    }
    catch (EvaluateException ex) {
      if (ex.getCause() instanceof VMDisconnectedException) {
        return false;
      }
      throw EvaluateExceptionUtil.createEvaluateException(
        DebuggerBundle.message("error.failed.evaluating.breakpoint.condition", getCondition(), ex.getMessage())
      );
    }
    return true;
  }

  protected String calculateEventClass(EvaluationContextImpl context, LocatableEvent event) throws EvaluateException {
    return event.location().declaringType().name();
  }

  private boolean typeMatchesClassFilters(@Nullable String typeName) {
    if (typeName == null) {
      return true;
    }
    boolean matches = false, hasEnabled = false;
    for (ClassFilter classFilter : getClassFilters()) {
      if (classFilter.isEnabled()) {
        hasEnabled = true;
        if (classFilter.matches(typeName)) {
          matches = true;
          break;
        }
      }
    }
    if(hasEnabled && !matches) {
      return false;
    }
    for (ClassFilter classFilter : getClassExclusionFilters()) {
      if (classFilter.isEnabled() && classFilter.matches(typeName)) {
        return false;
      }
    }
    return true;
  }

  private void handleTemporaryBreakpointHit(final DebugProcessImpl debugProcess) {
    // need to delete the request immediately, see IDEA-133978
    debugProcess.getRequestsManager().deleteRequest(this);

    debugProcess.addDebugProcessListener(new DebugProcessAdapter() {
      @Override
      public void resumed(SuspendContext suspendContext) {
        removeBreakpoint();
      }

      @Override
      public void processDetached(DebugProcess process, boolean closedByUser) {
        removeBreakpoint();
      }

      private void removeBreakpoint() {
        AppUIUtil.invokeOnEdt(new Runnable() {
          @Override
          public void run() {
            DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager().removeBreakpoint(Breakpoint.this);
          }
        });
        debugProcess.removeDebugProcessListener(this);
      }
    });
  }

  public void updateUI() {
  }

  public void readExternal(Element parentNode) throws InvalidDataException {
    FilteredRequestorImpl requestor = new FilteredRequestorImpl(myProject);
    requestor.readTo(parentNode, this);
    try {
      setEnabled(Boolean.valueOf(JDOMExternalizerUtil.readField(parentNode, "ENABLED")));
    }
    catch (Exception ignored) {
    }
    try {
      setLogEnabled(Boolean.valueOf(JDOMExternalizerUtil.readField(parentNode, "LOG_ENABLED")));
    }
    catch (Exception ignored) {
    }
    try {
      String logMessage = JDOMExternalizerUtil.readField(parentNode, LOG_MESSAGE_OPTION_NAME);
      if (logMessage != null && !logMessage.isEmpty()) {
        XExpressionImpl expression = XExpressionImpl.fromText(logMessage);
        XDebuggerHistoryManager.getInstance(myProject).addRecentExpression(XBreakpointActionsPanel.LOG_EXPRESSION_HISTORY_ID, expression);
        myXBreakpoint.setLogExpressionObject(expression);
        ((XBreakpointBase)myXBreakpoint).setLogExpressionEnabled(Boolean.valueOf(JDOMExternalizerUtil.readField(parentNode, "LOG_EXPRESSION_ENABLED")));
      }
    }
    catch (Exception ignored) {
    }
    try {
      setRemoveAfterHit(Boolean.valueOf(JDOMExternalizerUtil.readField(parentNode, "REMOVE_AFTER_HIT")));
    }
    catch (Exception ignored) {
    }
  }

  @Nullable
  public abstract PsiElement getEvaluationElement();

  protected TextWithImports getLogMessage() {
    return TextWithImportsImpl.fromXExpression(myXBreakpoint.getLogExpressionObject());
  }

  protected TextWithImports getCondition() {
    return TextWithImportsImpl.fromXExpression(myXBreakpoint.getConditionExpression());
  }

  public boolean isEnabled() {
    return myXBreakpoint.isEnabled();
  }

  public void setEnabled(boolean enabled) {
    myXBreakpoint.setEnabled(enabled);
  }

  protected boolean isLogEnabled() {
    return myXBreakpoint.isLogMessage();
  }

  public void setLogEnabled(boolean logEnabled) {
    myXBreakpoint.setLogMessage(logEnabled);
  }

  protected boolean isLogExpressionEnabled() {
    XExpression expression = myXBreakpoint.getLogExpressionObject();
    if (XDebuggerUtilImpl.isEmptyExpression(expression)) {
      return false;
    }
    return !getLogMessage().isEmpty();
  }

  @Override
  public boolean isCountFilterEnabled() {
    return getProperties().isCOUNT_FILTER_ENABLED();
  }
  public void setCountFilterEnabled(boolean enabled) {
    if (getProperties().setCOUNT_FILTER_ENABLED(enabled)) {
      fireBreakpointChanged();
    }
  }

  @Override
  public int getCountFilter() {
    return getProperties().getCOUNT_FILTER();
  }

  public void setCountFilter(int filter) {
    if (getProperties().setCOUNT_FILTER(filter)) {
      fireBreakpointChanged();
    }
  }

  @Override
  public boolean isClassFiltersEnabled() {
    return getProperties().isCLASS_FILTERS_ENABLED();
  }

  public void setClassFiltersEnabled(boolean enabled) {
    if (getProperties().setCLASS_FILTERS_ENABLED(enabled)) {
      fireBreakpointChanged();
    }
  }

  @Override
  public ClassFilter[] getClassFilters() {
    return getProperties().getClassFilters();
  }

  public void setClassFilters(ClassFilter[] filters) {
    if (getProperties().setClassFilters(filters)) {
      fireBreakpointChanged();
    }
  }

  @Override
  public ClassFilter[] getClassExclusionFilters() {
    return getProperties().getClassExclusionFilters();
  }

  protected void setClassExclusionFilters(ClassFilter[] filters) {
    if (getProperties().setClassExclusionFilters(filters)) {
      fireBreakpointChanged();
    }
  }

  @Override
  public boolean isInstanceFiltersEnabled() {
    return getProperties().isINSTANCE_FILTERS_ENABLED();
  }

  public void setInstanceFiltersEnabled(boolean enabled) {
    if (getProperties().setINSTANCE_FILTERS_ENABLED(enabled)) {
      fireBreakpointChanged();
    }
  }

  @Override
  public InstanceFilter[] getInstanceFilters() {
    return getProperties().getInstanceFilters();
  }

  public void setInstanceFilters(InstanceFilter[] filters) {
    if (getProperties().setInstanceFilters(filters)) {
      fireBreakpointChanged();
    }
  }

  private static String getSuspendPolicy(XBreakpoint breakpoint) {
    switch (breakpoint.getSuspendPolicy()) {
      case ALL:
        return DebuggerSettings.SUSPEND_ALL;
      case THREAD:
        return DebuggerSettings.SUSPEND_THREAD;
      case NONE:
        return DebuggerSettings.SUSPEND_NONE;

      default:
        throw new IllegalArgumentException("unknown suspend policy");
    }
  }

  static SuspendPolicy transformSuspendPolicy(String policy) {
    if (DebuggerSettings.SUSPEND_ALL.equals(policy)) {
      return SuspendPolicy.ALL;
    } else if (DebuggerSettings.SUSPEND_THREAD.equals(policy)) {
      return SuspendPolicy.THREAD;
    } else if (DebuggerSettings.SUSPEND_NONE.equals(policy)) {
      return SuspendPolicy.NONE;
    } else {
      throw new IllegalArgumentException("unknown suspend policy");
    }
  }

  protected boolean isSuspend() {
    return myXBreakpoint.getSuspendPolicy() != SuspendPolicy.NONE;
  }

  @Override
  public String getSuspendPolicy() {
    return getSuspendPolicy(myXBreakpoint);
  }

  public void setSuspendPolicy(String policy) {
    myXBreakpoint.setSuspendPolicy(transformSuspendPolicy(policy));
  }

  protected boolean isConditionEnabled() {
    XExpression condition = myXBreakpoint.getConditionExpression();
    if (XDebuggerUtilImpl.isEmptyExpression(condition)) {
      return false;
    }
    return !getCondition().isEmpty();
  }

  public void setCondition(@Nullable TextWithImports condition) {
    myXBreakpoint.setConditionExpression(TextWithImportsImpl.toXExpression(condition));
  }

  public void addInstanceFilter(long l) {
    getProperties().addInstanceFilter(l);
  }

  protected void fireBreakpointChanged() {
    ((XBreakpointBase)myXBreakpoint).fireBreakpointChanged();
  }
}
