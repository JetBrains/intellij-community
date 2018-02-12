/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

/*
 * Class Breakpoint
 * @author Jeka
 */
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.*;
import com.intellij.debugger.actions.ThreadDumpAction;
import com.intellij.debugger.engine.*;
import com.intellij.debugger.engine.evaluation.*;
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilderImpl;
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.debugger.engine.evaluation.expression.UnsupportedExpressionException;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.requests.ClassPrepareRequestor;
import com.intellij.debugger.requests.Requestor;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.impl.watch.CompilingEvaluatorImpl;
import com.intellij.debugger.ui.overhead.OverheadProducer;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiElement;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.util.ObjectUtils;
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
import com.sun.jdi.request.EventRequest;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.debugger.breakpoints.properties.JavaBreakpointProperties;

import javax.swing.*;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

public abstract class Breakpoint<P extends JavaBreakpointProperties> implements FilteredRequestor, ClassPrepareRequestor, OverheadProducer {
  public static final Key<Breakpoint> DATA_KEY = Key.create("JavaBreakpoint");
  private static final Key<Long> HIT_COUNTER = Key.create("HIT_COUNTER");

  final XBreakpoint<P> myXBreakpoint;
  protected final Project myProject;

  @NonNls private static final String LOG_MESSAGE_OPTION_NAME = "LOG_MESSAGE";
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
   * @param debugProcess the requesting process
   */
  public abstract void createRequest(DebugProcessImpl debugProcess);

  static boolean shouldCreateRequest(Requestor requestor, XBreakpoint xBreakpoint, DebugProcessImpl debugProcess, boolean forPreparedClass) {
    return ReadAction.compute(() -> {
      JavaDebugProcess process = debugProcess.getXdebugProcess();
      return process != null
             && debugProcess.isAttached()
             && (xBreakpoint == null || ((XDebugSessionImpl)process.getSession()).isBreakpointActive(xBreakpoint))
             && (forPreparedClass || debugProcess.getRequestsManager().findRequests(requestor).isEmpty());
    });
  }

  protected final boolean shouldCreateRequest(DebugProcessImpl debugProcess, boolean forPreparedClass) {
    return shouldCreateRequest(this, getXBreakpoint(), debugProcess, forPreparedClass);
  }

  protected final boolean shouldCreateRequest(DebugProcessImpl debugProcess) {
    return shouldCreateRequest(debugProcess, false);
  }

  /**
   * Request for creating all needed JPDA requests in the specified VM
   * @param debuggerProcess the requesting process
   */
  @Override
  public abstract void processClassPrepare(DebugProcess debuggerProcess, final ReferenceType referenceType);

  @Override
  public void customizeRenderer(SimpleColoredComponent renderer) {
    if (myXBreakpoint != null) {
      renderer.setIcon(myXBreakpoint.getType().getEnabledIcon());
    }
    else {
      renderer.setIcon(AllIcons.Debugger.Db_set_breakpoint);
    }
    renderer.append(getDisplayName());
  }

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

  protected String getStackTrace(LocatableEvent event) {
    StringBuilder builder = new StringBuilder(
      DebuggerBundle.message("status.line.breakpoint.reached.full.trace"));
    try {
      event.thread().frames().forEach(f -> builder.append("\n\t  ").append(ThreadDumpAction.renderLocation(f.location())));
    }
    catch (IncompatibleThreadStateException e) {
      builder.append("Stacktrace not available: ").append(e.getMessage());
    }
    return builder.toString();
  }

  public abstract boolean isValid();

  public abstract Key<? extends Breakpoint> getCategory();

  /**
   * Associates breakpoint with class.
   *    Create requests for loaded class and registers callback for loading classes
   * @param debugProcess the requesting process
   */
  protected void createOrWaitPrepare(DebugProcessImpl debugProcess, String classToBeLoaded) {
    debugProcess.getRequestsManager().callbackOnPrepareClasses(this, classToBeLoaded);
    processClassesPrepare(debugProcess, debugProcess.getVirtualMachineProxy().classesByName(classToBeLoaded).stream());
  }

  protected void createOrWaitPrepare(final DebugProcessImpl debugProcess, @NotNull final SourcePosition classPosition) {
    debugProcess.getRequestsManager().callbackOnPrepareClasses(this, classPosition);
    processClassesPrepare(debugProcess, debugProcess.getPositionManager().getAllClasses(classPosition).stream());
  }

  private void processClassesPrepare(DebugProcessImpl debugProcess, Stream<ReferenceType> classes) {
    classes.filter(ReferenceType::isPrepared).forEach(refType -> processClassPrepare(debugProcess, refType));
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
  public boolean processLocatableEvent(SuspendContextCommandImpl action, LocatableEvent event) throws EventProcessingException {
    SuspendContextImpl context = action.getSuspendContext();
    if (!isValid()) {
      context.getDebugProcess().getRequestsManager().deleteRequest(this);
      return false;
    }

    String title = DebuggerBundle.message("title.error.evaluating.breakpoint.condition");

    try {
      StackFrameProxyImpl frameProxy = context.getThread().frame(0);
      if (frameProxy == null) {
        // might be if the thread has been collected
        return false;
      }

      EvaluationContextImpl evaluationContext = new EvaluationContextImpl(context, frameProxy, () -> getThisObject(context, event));

      if (!evaluateCondition(evaluationContext, event)) {
        return false;
      }

      title = DebuggerBundle.message("title.error.evaluating.breakpoint.action");
      runAction(evaluationContext, event);
    }
    catch (final EvaluateException ex) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        System.out.println(ex.getMessage());
        return false;
      }

      throw new EventProcessingException(title, ex.getMessage(), ex);
    }

    return true;
  }

  private void runAction(EvaluationContextImpl context, LocatableEvent event) {
    DebugProcessImpl debugProcess = context.getDebugProcess();
    if (isLogEnabled() || isLogExpressionEnabled() || isLogStack()) {
      StringBuilder buf = new StringBuilder();
      if (myXBreakpoint.isLogMessage()) {
        buf.append(getEventMessage(event)).append("\n");
      }

      if (isLogStack()) {
        buf.append(getStackTrace(event)).append("\n");
      }

      if (isLogExpressionEnabled()) {
        if (!debugProcess.isAttached()) {
          return;
        }

        TextWithImports logMessage = getLogMessage();
        try {
          SourcePosition position = ContextUtil.getSourcePosition(context);
          PsiElement element = ContextUtil.getContextElement(context, position);
          ExpressionEvaluator evaluator = DebuggerInvocationUtil.commitAndRunReadAction(myProject,
            () -> EvaluatorCache.cacheOrGet("LogMessageEvaluator", event.request(), element, logMessage, () ->
              createExpressionEvaluator(myProject, element, position, logMessage, this::createLogMessageCodeFragment)));
          Value eval = evaluator.evaluate(context);
          buf.append(eval instanceof VoidValue ? "void" : DebuggerUtils.getValueAsString(context, eval));
        }
        catch (EvaluateException e) {
          buf.append(DebuggerBundle.message("error.unable.to.evaluate.expression"))
            .append(" \"").append(logMessage).append("\"")
            .append(" : ").append(e.getMessage());
        }
        buf.append("\n");
      }
      if (buf.length() > 0) {
        debugProcess.printToConsole(buf.toString());
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
    return Arrays.stream(getInstanceFilters()).anyMatch(instanceFilter -> instanceFilter.getId() == id);
  }

  public boolean evaluateCondition(final EvaluationContextImpl context, LocatableEvent event) throws EvaluateException {
    DebugProcessImpl debugProcess = context.getDebugProcess();
    if (isCountFilterEnabled() && !isConditionEnabled()) {
      debugProcess.getVirtualMachineProxy().suspend();
      debugProcess.getRequestsManager().deleteRequest(this);
      createRequest(debugProcess);
      debugProcess.getVirtualMachineProxy().resume();
    }
    if (isInstanceFiltersEnabled()) {
      Value value = context.computeThisObject();
      if (value != null) {  // non-static
        ObjectReference reference = (ObjectReference)value;
        if (!hasObjectID(reference.uniqueID())) {
          return false;
        }
      }
    }

    if (isClassFiltersEnabled() &&
        !typeMatchesClassFilters(calculateEventClass(context, event), getClassFilters(), getClassExclusionFilters())) {
      return false;
    }

    if (isConditionEnabled()) {
      TextWithImports condition = getCondition();
      if (condition.isEmpty()) {
        return true;
      }

      StackFrameProxyImpl frame = context.getFrameProxy();
      if (frame != null) {
        Location location = frame.location();
        if (location != null) {
          ThreeState result = debugProcess.getPositionManager().evaluateCondition(context, frame, location, condition.getText());
          if (result != ThreeState.UNSURE) {
            return result == ThreeState.YES;
          }
        }
      }

      try {
        SourcePosition contextSourcePosition = ContextUtil.getSourcePosition(context);
        ExpressionEvaluator evaluator = DebuggerInvocationUtil.commitAndRunReadAction(myProject, () -> {
          // IMPORTANT: calculate context psi element basing on the location where the exception
          // has been hit, not on the location where it was set. (For line breakpoints these locations are the same, however,
          // for method, exception and field breakpoints these locations differ)
          PsiElement contextElement = ContextUtil.getContextElement(contextSourcePosition);
          PsiElement contextPsiElement = contextElement != null ? contextElement : getEvaluationElement(); // as a last resort
          return EvaluatorCache.cacheOrGet("ConditionEvaluator", event.request(), contextPsiElement, condition,
                                           () -> createExpressionEvaluator(myProject,
                                                                           contextPsiElement,
                                                                           contextSourcePosition,
                                                                           condition,
                                                                           this::createConditionCodeFragment));
        });
        if (!DebuggerUtilsEx.evaluateBoolean(evaluator, context)) {
          return false;
        }
      }
      catch (EvaluateException ex) {
        if (ex.getCause() instanceof VMDisconnectedException) {
          return false;
        }
        throw EvaluateExceptionUtil.createEvaluateException(
          DebuggerBundle.message("error.failed.evaluating.breakpoint.condition", condition, ex.getMessage())
        );
      }
    }
    if (isCountFilterEnabled() && isConditionEnabled()) {
      Long hitCount = ObjectUtils.notNull((Long)event.request().getProperty(HIT_COUNTER), 0L) + 1;
      event.request().putProperty(HIT_COUNTER, hitCount);
      return hitCount % getCountFilter() == 0;
    }
    return true;
  }

  private static class EvaluatorCache {
    private final PsiElement myContext;
    private final TextWithImports myTextWithImports;
    private final ExpressionEvaluator myEvaluator;

    private EvaluatorCache(PsiElement context, TextWithImports textWithImports, ExpressionEvaluator evaluator) {
      myContext = context;
      myTextWithImports = textWithImports;
      myEvaluator = evaluator;
    }

    @Nullable
    static ExpressionEvaluator cacheOrGet(String propertyName,
                                          EventRequest request,
                                          PsiElement context,
                                          TextWithImports text,
                                          EvaluatingComputable<ExpressionEvaluator> supplier) throws EvaluateException {
      EvaluatorCache cache = (EvaluatorCache)request.getProperty(propertyName);
      if (cache != null && Objects.equals(cache.myContext, context) && Objects.equals(cache.myTextWithImports, text)) {
        return cache.myEvaluator;
      }
      ExpressionEvaluator evaluator = supplier.compute();
      request.putProperty(propertyName, new EvaluatorCache(context, text, evaluator));
      return evaluator;
    }
  }

  private static ExpressionEvaluator createExpressionEvaluator(Project project,
                                                               PsiElement contextPsiElement,
                                                               SourcePosition contextSourcePosition,
                                                               TextWithImports text,
                                                               Function<PsiElement, PsiCodeFragment> fragmentFactory)
    throws EvaluateException {
    try {
      return EvaluatorBuilderImpl.build(text, contextPsiElement, contextSourcePosition, project);
    }
    catch (UnsupportedExpressionException ex) {
      ExpressionEvaluator eval = CompilingEvaluatorImpl.create(project, contextPsiElement, fragmentFactory);
      if (eval != null) {
        return eval;
      }
      throw ex;
    }
  }

  private PsiCodeFragment createConditionCodeFragment(PsiElement context) {
    return createCodeFragment(myProject, getCondition(), context);
  }

  private PsiCodeFragment createLogMessageCodeFragment(PsiElement context) {
    return createCodeFragment(myProject, getLogMessage(), context);
  }

  private static PsiCodeFragment createCodeFragment(Project project, TextWithImports text, PsiElement context) {
    return DebuggerUtilsEx.findAppropriateCodeFragmentFactory(text, context).createCodeFragment(text, context, project);
  }

  protected String calculateEventClass(EvaluationContextImpl context, LocatableEvent event) throws EvaluateException {
    return event.location().declaringType().name();
  }

  protected static boolean typeMatchesClassFilters(@Nullable String typeName, ClassFilter[] includeFilters, ClassFilter[] exludeFilters) {
    if (typeName == null) {
      return true;
    }
    boolean matches = false, hasEnabled = false;
    for (ClassFilter classFilter : includeFilters) {
      if (classFilter.isEnabled()) {
        hasEnabled = true;
        if (classFilter.matches(typeName)) {
          matches = true;
          break;
        }
      }
    }
    if (hasEnabled && !matches) {
      return false;
    }
    return Arrays.stream(exludeFilters).noneMatch(classFilter -> classFilter.isEnabled() && classFilter.matches(typeName));
  }

  private void handleTemporaryBreakpointHit(final DebugProcessImpl debugProcess) {
    // need to delete the request immediately, see IDEA-133978
    debugProcess.getRequestsManager().deleteRequest(this);

    debugProcess.addDebugProcessListener(new DebugProcessListener() {
      @Override
      public void resumed(SuspendContext suspendContext) {
        removeBreakpoint();
      }

      @Override
      public void processDetached(DebugProcess process, boolean closedByUser) {
        removeBreakpoint();
      }

      private void removeBreakpoint() {
        AppUIUtil.invokeOnEdt(() -> DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager().removeBreakpoint(Breakpoint.this));
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

  protected boolean isLogStack() {
    return myXBreakpoint.isLogStack();
  }

  protected boolean isLogExpressionEnabled() {
    if (XDebuggerUtilImpl.isEmptyExpression(myXBreakpoint.getLogExpressionObject())) {
      return false;
    }
    return !getLogMessage().isEmpty();
  }

  @Override
  public boolean isCountFilterEnabled() {
    return getProperties().isCOUNT_FILTER_ENABLED() && getCountFilter() > 0;
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

  public void setClassExclusionFilters(ClassFilter[] filters) {
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

  public boolean isConditionEnabled() {
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
