// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * Class MethodBreakpoint
 * @author Jeka
 */
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.JVMName;
import com.intellij.debugger.engine.JVMNameUtil;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.requests.RequestManagerImpl;
import com.intellij.debugger.impl.DebuggerUtilsAsync;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.impl.DebuggerUtilsImpl;
import com.intellij.debugger.impl.PositionUtil;
import com.intellij.debugger.jdi.ClassesByNameProvider;
import com.intellij.debugger.jdi.MethodBytecodeUtil;
import com.intellij.debugger.requests.Requestor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.DocumentUtil;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.containers.MultiMap;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointListener;
import com.sun.jdi.*;
import com.sun.jdi.event.LocatableEvent;
import com.sun.jdi.event.MethodEntryEvent;
import com.sun.jdi.event.MethodExitEvent;
import com.sun.jdi.request.*;
import one.util.streamex.StreamEx;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.debugger.breakpoints.properties.JavaMethodBreakpointProperties;
import org.jetbrains.org.objectweb.asm.MethodVisitor;
import org.jetbrains.org.objectweb.asm.Opcodes;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public class MethodBreakpoint extends BreakpointWithHighlighter<JavaMethodBreakpointProperties> implements MethodBreakpointBase {
  private static final Logger LOG = Logger.getInstance(MethodBreakpoint.class);
  protected @Nullable JVMName mySignature;

  public static final @NonNls Key<MethodBreakpoint> CATEGORY = BreakpointCategory.lookup("method_breakpoints");

  protected MethodBreakpoint(@NotNull Project project, XBreakpoint breakpoint) {
    super(project, breakpoint);
  }

  @Override
  public @NotNull Key<MethodBreakpoint> getCategory() {
    return CATEGORY;
  }

  @Override
  public boolean isValid() {
    return super.isValid() && getMethodName() != null;
  }

  @RequiresBackgroundThread
  @Override
  public void reload() {
    super.reload();

    setMethodName(null);
    mySignature = null;

    SourcePosition sourcePosition = getSourcePosition();
    if (sourcePosition != null) {
      MethodDescriptor descriptor = getMethodDescriptor(myProject, sourcePosition);
      if (descriptor != null) {
        setMethodName(descriptor.methodName);
        mySignature = descriptor.methodSignature;
        if (descriptor.isStatic) {
          setInstanceFiltersEnabled(false);
        }
      }
    }
    PsiClass psiClass = getPsiClass();
    if (psiClass != null) {
      getProperties().myClassPattern = psiClass.getQualifiedName();
    }
  }

  private static void createRequestForSubClasses(@NotNull MethodBreakpointBase breakpoint,
                                                 @NotNull DebugProcessImpl debugProcess,
                                                 @NotNull ReferenceType baseType) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    RequestManagerImpl requestsManager = debugProcess.getRequestsManager();
    ClassPrepareRequest request = requestsManager.createClassPrepareRequest((debuggerProcess, referenceType) -> {
      if (DebuggerUtilsImpl.instanceOf(referenceType, baseType)) {
        createRequestForPreparedClassEmulated(breakpoint, debugProcess, referenceType, false);
      }
    }, null);
    if (request != null) {
      requestsManager.registerRequest(breakpoint, request);
      request.enable();
    }

    ProgressWindow indicator =
      new ProgressWindow(true, false, debugProcess.getProject(), JavaDebuggerBundle.message("cancel.emulation"));
    indicator.setDelayInMillis(2000);

    AtomicBoolean changed = new AtomicBoolean();
    XBreakpointListener<XBreakpoint<?>> listener = new XBreakpointListener<XBreakpoint<?>>() {
      void changed(@NotNull XBreakpoint b) {
        if (b == breakpoint.getXBreakpoint()) {
          changed.set(true);
          indicator.cancel();
        }
      }

      @Override
      public void breakpointRemoved(@NotNull XBreakpoint b) {
        changed(b);
      }

      @Override
      public void breakpointChanged(@NotNull XBreakpoint b) {
        changed(b);
      }
    };

    debugProcess.getProject().getMessageBus().connect(indicator).subscribe(XBreakpointListener.TOPIC, listener);
    try {
      ProgressManager.getInstance().executeProcessUnderProgress(
        () -> processPreparedSubTypes(baseType,
                                      (subType, classesByName) ->
                                        createRequestForPreparedClassEmulated(breakpoint, debugProcess, subType, classesByName, false),
                                      indicator),
        indicator);
      if (indicator.isCanceled() && !changed.get()) {
        breakpoint.disableEmulation();
      }
    }
    catch (ProcessCanceledException e) {
      breakpoint.disableEmulation();
    }
  }

  @Override
  public void disableEmulation() {
    MethodBreakpointBase.disableEmulation(this);
  }

  static void createRequestForPreparedClassEmulated(@NotNull MethodBreakpointBase breakpoint,
                                                    @NotNull DebugProcessImpl debugProcess,
                                                    @NotNull ReferenceType classType,
                                                    boolean base) {
    createRequestForPreparedClassEmulated(breakpoint, debugProcess, classType, classType.virtualMachine()::classesByName, base);
  }

  static void createRequestForPreparedClassEmulated(@NotNull MethodBreakpointBase breakpoint,
                                                    @NotNull DebugProcessImpl debugProcess,
                                                    @NotNull ReferenceType classType,
                                                    @NotNull ClassesByNameProvider classesByName,
                                                    boolean base) {
    if (breakpoint.isWatchExit() && !MethodBreakpointBase.canBeWatchExitEmulated(classType.virtualMachine())) {
      breakpoint.disableEmulation();
      return;
    }
    if (!base && !shouldCreateRequest(breakpoint, breakpoint.getXBreakpoint(), debugProcess, true)) {
      return;
    }
    Method lambdaMethod = MethodBytecodeUtil.getLambdaMethod(classType, classesByName);
    if (lambdaMethod != null &&
        breakpoint
          .matchingMethods(StreamEx.of(((ClassType)classType).interfaces()).flatCollection(ReferenceType::allMethods), debugProcess)
          .findFirst().isEmpty()) {
      return;
    }
    StreamEx<Method> methods = lambdaMethod != null
                               ? StreamEx.of(lambdaMethod)
                               : breakpoint.matchingMethods(StreamEx.of(classType.methods()).filter(m -> base || !m.isAbstract()), debugProcess);
    boolean found = false;
    for (Method original : methods) {
      found = true;

      Method bridgeTarget = MethodBytecodeUtil.getBridgeTargetMethod(original, classesByName);
      Method method = bridgeTarget != null ? bridgeTarget : original;

      if (method.isNative()) {
        LOG.info("Breakpoint emulation was disabled because " + method + " is native");
        breakpoint.disableEmulation();
        return;
      }
      if (method.isAbstract()) {
        continue;
      }

      if (breakpoint.isWatchEntry()) {
        // We assume that all VMs start code indexes from zero.
        Location location = new LocationCodeIndexOnly(method, 0);
        createLocationBreakpointRequest(breakpoint, location, debugProcess, true);
      }

      if (breakpoint.isWatchExit()) {
        class BytecodeVisitor extends MethodVisitor implements MethodBytecodeUtil.InstructionOffsetReader {
          private int bytecodeOffset = -1;

          BytecodeVisitor() {
            super(Opcodes.API_VERSION);
          }

          @Override
          public void readBytecodeInstructionOffset(int offset) {
            bytecodeOffset = offset;
          }

          @Override
          public void visitInsn(int opcode) {
            if (Opcodes.IRETURN <= opcode && opcode <= Opcodes.RETURN) {
              assert bytecodeOffset >= 0;
              Location location = new LocationCodeIndexOnly(method, bytecodeOffset);
              createLocationBreakpointRequest(breakpoint, location, debugProcess, false);
            }
          }
        }
        MethodBytecodeUtil.visit(method, new BytecodeVisitor(), false);
      }
    }
    if (base && found) {
      // desired class found - now also track all new classes
      createRequestForSubClasses(breakpoint, debugProcess, classType);
    }
  }

  private static void createLocationBreakpointRequest(@NotNull FilteredRequestor requestor,
                                                      @Nullable Location location,
                                                      @NotNull DebugProcessImpl debugProcess,
                                                      boolean methodEntry) {
    BreakpointRequest request = createLocationBreakpointRequest(requestor, location, debugProcess);
    if (request != null) {
      request.putProperty(METHOD_ENTRY_KEY, methodEntry);
    }
  }

  @Override
  protected void createRequestForPreparedClass(@NotNull DebugProcessImpl debugProcess, @NotNull ReferenceType classType) {
    if (isEmulated()) {
      createRequestForPreparedClassEmulated(this, debugProcess, classType, true);
    }
    else {
      createRequestForPreparedClassOriginal(debugProcess, classType);
    }
  }

  /**
   * Return `true` if the method has the same name and signature as the breakpoint.
   */
  protected boolean isMethodMatch(@NotNull Method method, @NotNull DebugProcessImpl debugProcess) {
    try {
      String name = getMethodName();
      return
        name != null && name.equals(method.name()) &&
        mySignature != null && mySignature.getName(debugProcess).equals(method.signature());
    }
    catch (EvaluateException e) {
      LOG.debug("Should not happen. mySignature is a JVMRawText and it doesn't throw", e);
      return false;
    }
  }

  private void createRequestForPreparedClassOriginal(@NotNull DebugProcessImpl debugProcess, @NotNull ReferenceType classType) {
    boolean hasMethod = false;
    for (Method method : classType.allMethods()) {
      if (isMethodMatch(method, debugProcess)) {
        hasMethod = true;
        break;
      }
    }

    if (!hasMethod) {
      debugProcess.getRequestsManager().setInvalid(
        this, JavaDebuggerBundle.message("error.invalid.breakpoint.method.not.found", classType.name())
      );
      return;
    }

    RequestManagerImpl requestManager = debugProcess.getRequestsManager();
    if (isWatchEntry()) {
      MethodEntryRequest entryRequest = findRequest(debugProcess, MethodEntryRequest.class, this);
      if (entryRequest == null) {
        entryRequest = requestManager.createMethodEntryRequest(this);
      }
      else {
        entryRequest.disable();
      }
      //entryRequest.addClassFilter(myClassQualifiedName);
      // use addClassFilter(ReferenceType) in order to stop on subclasses also!
      entryRequest.addClassFilter(classType);
      debugProcess.getRequestsManager().enableRequest(entryRequest);
    }
    if (isWatchExit()) {
      MethodExitRequest exitRequest = findRequest(debugProcess, MethodExitRequest.class, this);
      if (exitRequest == null) {
        exitRequest = requestManager.createMethodExitRequest(this);
      }
      else {
        exitRequest.disable();
      }
      //exitRequest.addClassFilter(myClassQualifiedName);
      exitRequest.addClassFilter(classType);
      debugProcess.getRequestsManager().enableRequest(exitRequest);
    }
  }

  @Override
  public String getEventMessage(@NotNull LocatableEvent event) {
    return getEventMessage(event, getFileName());
  }

  static @Nls String getEventMessage(@NotNull LocatableEvent event, @NotNull String defaultFileName) {
    Location location = event.location();
    if (event instanceof MethodEntryEvent) {
      return getEventMessage(true, ((MethodEntryEvent)event).method(), location, defaultFileName);
    }
    if (event instanceof MethodExitEvent) {
      return getEventMessage(false, ((MethodExitEvent)event).method(), location, defaultFileName);
    }
    Object entryProperty = event.request().getProperty(METHOD_ENTRY_KEY);
    if (entryProperty instanceof Boolean) {
      return getEventMessage((Boolean)entryProperty, location.method(), location, defaultFileName);
    }
    return "";
  }

  private static @Nls String getEventMessage(boolean entry, Method method, Location location, @NotNull String defaultFileName) {
    String locationQName = DebuggerUtilsEx.getLocationMethodQName(location);
    String locationFileName = DebuggerUtilsEx.getSourceName(location, defaultFileName);
    int locationLine = location.lineNumber();
    return JavaDebuggerBundle.message(entry ? "status.method.entry.breakpoint.reached" : "status.method.exit.breakpoint.reached",
                                      method.declaringType().name() + "." + method.name() + "()",
                                      locationQName,
                                      locationFileName,
                                      locationLine
    );
  }

  @Override
  public PsiElement getEvaluationElement() {
    return getPsiClass();
  }

  @Override
  protected Icon getDisabledIcon(boolean isMuted) {
    if (DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager().findMasterBreakpoint(this) != null && isMuted) {
      return AllIcons.Debugger.Db_muted_dep_method_breakpoint;
    }
    return null;
  }

  @Override
  protected Icon getVerifiedIcon(boolean isMuted) {
    return isSuspend() ? AllIcons.Debugger.Db_verified_method_breakpoint : AllIcons.Debugger.Db_verified_no_suspend_method_breakpoint;
  }

  @Override
  protected @NotNull Icon getVerifiedWarningsIcon(boolean isMuted) {
    return LayeredIcon.layeredIcon(new Icon[]{isMuted ? AllIcons.Debugger.Db_muted_method_breakpoint : AllIcons.Debugger.Db_method_breakpoint,
                               AllIcons.General.WarningDecorator});
  }

  @Override
  public String getDisplayName() {
    final @Nls StringBuilder buffer = new StringBuilder();
    if (isValid()) {
      final String className = getClassName();
      final boolean classNameExists = className != null && !className.isEmpty();
      if (classNameExists) {
        buffer.append(className);
      }
      if (getMethodName() != null) {
        if (classNameExists) {
          buffer.append(".");
        }
        buffer.append(getMethodName());
      }
    }
    else {
      buffer.append(JavaDebuggerBundle.message("status.breakpoint.invalid"));
    }
    return buffer.toString();
  }

  @Override
  public boolean evaluateCondition(@NotNull EvaluationContextImpl context, @NotNull LocatableEvent event) throws EvaluateException {
    if (!matchesEvent(event, context.getDebugProcess())) {
      return false;
    }
    return super.evaluateCondition(context, event);
  }

  public boolean matchesEvent(final @NotNull LocatableEvent event, final DebugProcessImpl process) {
    if (isEmulated()) {
      return true;
    }
    if (getMethodName() == null || mySignature == null) {
      return false;
    }
    final Method method = event.location().method();
    return isMethodMatch(method, process);
  }

  public static @Nullable MethodBreakpoint create(@NotNull Project project, XBreakpoint xBreakpoint) {
    final MethodBreakpoint breakpoint = new MethodBreakpoint(project, xBreakpoint);
    return (MethodBreakpoint)breakpoint.init();
  }


  //public boolean canMoveTo(final SourcePosition position) {
  //  return super.canMoveTo(position) && PositionUtil.getPsiElementAt(getProject(), PsiMethod.class, position) != null;
  //}

  /**
   * finds FQ method's class name and method's signature
   */
  private static @Nullable MethodDescriptor getMethodDescriptor(final @NotNull Project project, final @NotNull SourcePosition sourcePosition) {
    Document document = sourcePosition.getFile().getViewProvider().getDocument();
    if (document == null) {
      return null;
    }
    //final int endOffset = document.getLineEndOffset(sourcePosition);
    //final MethodDescriptor descriptor = docManager.commitAndRunReadAction(new Computable<MethodDescriptor>() {
    // conflicts with readAction on initial breakpoints creation
    final MethodDescriptor descriptor = ReadAction.compute(() -> {
      //PsiMethod method = DebuggerUtilsEx.findPsiMethod(psiJavaFile, endOffset);
      PsiMethod method = PositionUtil.getPsiElementAt(project, PsiMethod.class, sourcePosition);
      if (method == null) {
        return null;
      }
      final int methodOffset = method.getTextOffset();
      if (!DocumentUtil.isValidOffset(methodOffset, document) || document.getLineNumber(methodOffset) < sourcePosition.getLine()) {
        return null;
      }

      final PsiIdentifier identifier = method.getNameIdentifier();
      int methodNameOffset = identifier != null ? identifier.getTextOffset() : methodOffset;
      final MethodDescriptor res =
        new MethodDescriptor();
      res.methodName = JVMNameUtil.getJVMMethodName(method);
      try {
        res.methodSignature = JVMNameUtil.getJVMSignature(method);
        res.isStatic = method.hasModifierProperty(PsiModifier.STATIC);
      }
      catch (IndexNotReadyException ignored) {
        return null;
      }
      res.methodLine = document.getLineNumber(methodNameOffset);
      return res;
    });
    if (descriptor == null || descriptor.methodName == null || descriptor.methodSignature == null) {
      return null;
    }
    return descriptor;
  }

  static @Nullable <T extends EventRequest> T findRequest(@NotNull DebugProcessImpl debugProcess, Class<T> requestClass, Requestor requestor) {
    return StreamEx.of(debugProcess.getRequestsManager().findRequests(requestor)).select(requestClass).findFirst().orElse(null);
  }

  @Override
  public void readExternal(@NotNull Element breakpointNode) throws InvalidDataException {
    super.readExternal(breakpointNode);
    try {
      getProperties().WATCH_ENTRY = Boolean.parseBoolean(JDOMExternalizerUtil.readField(breakpointNode, "WATCH_ENTRY"));
    }
    catch (Exception ignored) {
    }
    try {
      getProperties().WATCH_EXIT = Boolean.parseBoolean(JDOMExternalizerUtil.readField(breakpointNode, "WATCH_EXIT"));
    }
    catch (Exception ignored) {
    }
  }

  public boolean isEmulated() {
    return getProperties().EMULATED;
  }

  @Override
  public boolean isWatchEntry() {
    return getProperties().WATCH_ENTRY;
  }

  @Override
  public boolean isWatchExit() {
    return getProperties().WATCH_EXIT;
  }

  @Override
  public StreamEx<Method> matchingMethods(StreamEx<Method> methods, DebugProcessImpl debugProcess) {
    try {
      String methodName = getMethodName();
      String signature = mySignature != null ? mySignature.getName(debugProcess) : null;
      return methods.filter(m -> Objects.equals(methodName, m.name()) && Objects.equals(signature, m.signature())).limit(1);
    }
    catch (EvaluateException e) {
      LOG.warn(e);
    }
    return StreamEx.empty();
  }

  protected @Nullable String getMethodName() {
    return getProperties().myMethodName;
  }

  protected void setMethodName(@Nullable String methodName) {
    getProperties().myMethodName = methodName;
  }

  public static final class MethodDescriptor {
    public String methodName;
    public JVMName methodSignature;
    public boolean isStatic;
    public int methodLine;
  }

  private static void processPreparedSubTypes(ReferenceType classType,
                                              BiConsumer<? super ReferenceType, ? super ClassesByNameProvider> consumer,
                                              ProgressIndicator progressIndicator) {
    long start = 0;
    if (LOG.isDebugEnabled()) {
      start = System.currentTimeMillis();
    }
    progressIndicator.setIndeterminate(false);
    progressIndicator.start();
    progressIndicator.setText(JavaDebuggerBundle.message("label.method.breakpoints.processing.classes"));
    try {
      MultiMap<ReferenceType, ReferenceType> inheritance = MultiMap.createConcurrentSet();
      List<ReferenceType> allTypes = classType.virtualMachine().allClasses();
      int allSize = allTypes.size();
      List<CompletableFuture> futures = new ArrayList<>();
      AtomicInteger processed = new AtomicInteger();
      for (ReferenceType type : allTypes) {
        progressIndicator.checkCanceled();
        if (type.isPrepared()) {
          futures.add(DebuggerUtilsAsync.supertypes(type)
                        .thenAccept(supertypes -> supertypes.forEach(st -> inheritance.putValue(st, type)))
                        .exceptionally(throwable -> {
                          throwable = DebuggerUtilsAsync.unwrap(throwable);
                          if (throwable instanceof ObjectCollectedException) {
                            return null;
                          }
                          throw new CompletionException(throwable);
                        })
                        .thenRun(() -> updateProgress(progressIndicator, processed.incrementAndGet(), allSize)));
        }
      }
      ProgressIndicatorUtils.awaitWithCheckCanceled(CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])), progressIndicator);
      List<ReferenceType> types = StreamEx.ofTree(classType, t -> StreamEx.of(inheritance.get(t))).skip(1).toList();

      if (LOG.isDebugEnabled()) {
        long current = System.currentTimeMillis();
        LOG.debug("Processed  " + allSize + " classes in " + (current - start) + "ms");
        start = current;
      }

      progressIndicator.setText(JavaDebuggerBundle.message("label.method.breakpoints.setting.breakpoints"));

      ClassesByNameProvider classesByName = ClassesByNameProvider.createCache(allTypes);

      int typesSize = types.size();
      for (int i = 0; i < typesSize; i++) {
        progressIndicator.checkCanceled();
        consumer.accept(types.get(i), classesByName);
        updateProgress(progressIndicator, i, typesSize);
      }

      if (LOG.isDebugEnabled()) {
        LOG.debug("Created " + typesSize + " requests in " + (System.currentTimeMillis() - start) + "ms");
      }
    }
    finally {
      progressIndicator.stop();
    }
  }

  private static void updateProgress(ProgressIndicator progressIndicator, int current, int total) {
    progressIndicator.setText2(current + "/" + total);
    progressIndicator.setFraction((double)current / total);
  }

  /**
   * Optimized {@link Location} which should be used only to create breakpoint
   * at known valid code index.
   * <p/>
   * The key difference with {@link com.jetbrains.jdi.ConcreteMethodImpl#locationOfCodeIndex(long)}
   * is an absence of index validity checks, which normally would require to load line number information.
   */
  private static class LocationCodeIndexOnly implements Location {
    private final Method method;
    private final long codeIndex;

    public LocationCodeIndexOnly(Method method, long codeIndex) {
      assert !method.isNative() && !method.isAbstract();
      assert codeIndex >= 0;

      this.method = method;
      this.codeIndex = codeIndex;
    }

    @Override
    public VirtualMachine virtualMachine() {
      return method.virtualMachine();
    }

    @Override
    public ReferenceType declaringType() {
      return method.declaringType();
    }

    @Override
    public Method method() {
      return method;
    }

    @Override
    public long codeIndex() {
      return codeIndex;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) return true;
      if (other == null || getClass() != other.getClass()) return false;
      LocationCodeIndexOnly that = (LocationCodeIndexOnly)other;
      return this.codeIndex == that.codeIndex &&
             this.method.equals(that.method);
    }

    @Override
    public int hashCode() {
      return Objects.hash(method, codeIndex);
    }

    @Override
    public int compareTo(@NotNull Location that) {
      // Same as in LocationImpl
      int res = this.method().compareTo(that.method());
      if (res != 0) {
        return res;
      }
      return Long.compare(codeIndex(), that.codeIndex());
    }

    // region Absent information about source code
    @Override
    public int lineNumber() {
      return -1;
    }

    @Override
    public int lineNumber(String stratum) {
      return -1;
    }

    @Override
    public String sourceName() throws AbsentInformationException {
      throw new AbsentInformationException();
    }

    @Override
    public String sourceName(String stratum) throws AbsentInformationException {
      throw new AbsentInformationException();
    }

    @Override
    public String sourcePath() throws AbsentInformationException {
      throw new AbsentInformationException();
    }

    @Override
    public String sourcePath(String stratum) throws AbsentInformationException {
      throw new AbsentInformationException();
    }

    @Override
    public String toString() {
      return "LocationCodeIndexOnly{" +
             "method=" + method +
             ", codeIndex=" + codeIndex +
             '}';
    }
    // endregion
  }
}
