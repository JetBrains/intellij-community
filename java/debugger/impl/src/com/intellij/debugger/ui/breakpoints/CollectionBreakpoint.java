// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.breakpoints;


import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.engine.requests.RequestManagerImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.impl.DebuggerUtilsImpl;
import com.intellij.debugger.impl.PositionUtil;
import com.intellij.debugger.jdi.MethodBytecodeUtil;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.SlowOperations;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.sun.jdi.*;
import com.sun.jdi.event.LocatableEvent;
import com.sun.jdi.event.ModificationWatchpointEvent;
import com.sun.jdi.request.*;
import org.jetbrains.annotations.*;
import org.jetbrains.java.debugger.breakpoints.properties.JavaCollectionBreakpointProperties;
import org.jetbrains.org.objectweb.asm.Label;
import org.jetbrains.org.objectweb.asm.MethodVisitor;
import org.jetbrains.org.objectweb.asm.Opcodes;

import javax.swing.*;
import java.util.*;
import java.util.stream.Collectors;

@ApiStatus.Experimental
public class CollectionBreakpoint extends BreakpointWithHighlighter<JavaCollectionBreakpointProperties> {
  @NonNls public static final Key<CollectionBreakpoint> CATEGORY = BreakpointCategory.lookup("collection_breakpoints");

  private static final String INSTRUMENTOR_CLASS = "com.intellij.rt.debugger.agent.CollectionBreakpointInstrumentor";
  private static final String CONSTRUCTOR_METHOD_NAME = "<init>";
  private static final String STATIC_BLOCK_METHOD_NAME = "<clinit>";
  private static final String GET_INTERNAL_CLS_NAME_METHOD_NAME = "getInternalClsName";
  private static final String GET_INTERNAL_CLS_NAME_METHOD_DESCRIPTOR = "(Ljava/lang/String;)Ljava/lang/String;";
  private static final String EMULATE_FIELD_WATCHPOINT_METHOD_NAME = "emulateFieldWatchpoint";
  private static final String EMULATE_FIELD_WATCHPOINT_METHOD_DESCRIPTOR = "([Ljava/lang/String;)V";
  private static final String PUT_FIELD_TO_CAPTURE_METHOD_NAME = "putFieldToCapture";
  private static final String PUT_FIELD_TO_CAPTURE_METHOD_DESCRIPTOR = "(Ljava/lang/String;Ljava/lang/String;)V";
  private static final String CAPTURE_FIELD_MODIFICATION_METHOD_NAME = "captureFieldModification";
  private static final String CAPTURE_FIELD_MODIFICATION_METHOD_DESCRIPTOR = "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Z)V";
  private static final String CAPTURE_COLLECTION_MODIFICATION_DEFAULT_METHOD_NAME = "captureCollectionModification";
  private static final String CAPTURE_COLLECTION_MODIFICATION_DEFAULT_METHOD_DESCRIPTOR = "(Lcom/intellij/rt/debugger/agent/CollectionBreakpointInstrumentor$Multiset;Ljava/lang/Object;)V";
  private static final String CAPTURE_COLLECTION_MODIFICATION_SPECIAL_METHOD_NAME = "captureCollectionModification";
  private static final String CAPTURE_COLLECTION_MODIFICATION_SPECIAL_METHOD_DESCRIPTOR = "(ZZLjava/lang/Object;Ljava/lang/Object;Z)V";
  private static final long MAX_INSTANCES_NUMBER = 1000000;
  private final Map<String, String> myUnprocessedClasses = new HashMap<>();
  private final Map<String, String> myClassesNames = new HashMap<>();
  private volatile boolean myBaseClsPrepared = false;
  private volatile boolean myIsStatic = false;
  private volatile boolean myIsPrivate = false;


  protected CollectionBreakpoint(Project project, XBreakpoint breakpoint) {
    super(project, breakpoint);
  }

  @Override
  public void reload() {
    super.reload();
    PsiField field = PositionUtil.getPsiElementAt(myProject, PsiField.class, getSourcePosition());
    if (field != null) {
      getProperties().myFieldName = field.getName();
      PsiModifierList modifierList = field.getModifierList();
      myIsPrivate = modifierList != null && modifierList.hasModifierProperty(PsiModifier.PRIVATE);
      PsiClass psiClass = field.getContainingClass();
      if (psiClass != null) {
        getProperties().myClassName = psiClass.getQualifiedName();
      }
      myIsStatic = SlowOperations.allowSlowOperations(() -> field.hasModifierProperty(PsiModifier.STATIC));
    }
    myBaseClsPrepared = false;
  }

  @Nls
  @Override
  public String getEventMessage(LocatableEvent event) {
    return "";
  }

  @Override
  public void createRequestForPreparedClass(DebugProcessImpl debugProcess, ReferenceType refType) {
    if (myBaseClsPrepared) {
      return;
    }
    createRequestForClass(debugProcess, refType);
    if (!isPrivate()) {
      createRequestForSubclasses(debugProcess, refType);
    }
    myBaseClsPrepared = true;
  }

  @Override
  protected Icon getDisabledIcon(boolean isMuted) {
    if (DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager().findMasterBreakpoint(this) != null && isMuted) {
      return AllIcons.Debugger.Db_muted_dep_field_breakpoint;
    }
    return null;
  }

  @Override
  protected Icon getVerifiedIcon(boolean isMuted) {
    return isSuspend() ? AllIcons.Debugger.Db_verified_field_breakpoint : AllIcons.Debugger.Db_verified_no_suspend_field_breakpoint;
  }

  @Override
  protected Icon getVerifiedWarningsIcon(boolean isMuted) {
    return new LayeredIcon(isMuted ? AllIcons.Debugger.Db_muted_field_breakpoint : AllIcons.Debugger.Db_field_breakpoint,
                           AllIcons.General.WarningDecorator);
  }

  @Override
  public boolean processLocatableEvent(@NotNull SuspendContextCommandImpl action, LocatableEvent event) throws EventProcessingException {
    SuspendContextImpl context = action.getSuspendContext();
    if (context == null) {
      return false;
    }

    DebugProcessImpl debugProcess = context.getDebugProcess();

    debugProcess.getRequestsManager().deleteRequest(this); // delete method entry request

    Location location = event.location();
    String methodName = location == null ? null : location.method().name();

    processClassesAndInstancesInJVM(context);

    if (STATIC_BLOCK_METHOD_NAME.equals(methodName)) {
      processStaticBlockEntry(context, event);
    }
    else if (CONSTRUCTOR_METHOD_NAME.equals(methodName)) {
      processConstructorEntry(context, event);
    }
    else {
      emulateFieldWatchpoint(debugProcess, context, location);
    }

    return false;
  }

  @Override
  public @Nullable PsiElement getEvaluationElement() {
    return getPsiClass();
  }

  @Override
  protected @Nullable ObjectReference getThisObject(SuspendContextImpl context, LocatableEvent event) {
    try {
      return super.getThisObject(context, event);
    }
    catch (EvaluateException e) {
      return null;
    }
  }

  private void createRequestForClass(DebugProcessImpl debugProcess, ReferenceType refType) {
    String clsName = refType.name();
    String signature = refType.signature();
    myClassesNames.put(clsName, signature);
    myUnprocessedClasses.put(clsName, signature);
    createMethodEntryRequest(debugProcess, refType);
  }

  private void processStaticBlockEntry(SuspendContextImpl context, LocatableEvent event) {
    DebugProcessImpl debugProcess = context.getDebugProcess();

    if (!isStatic()) {
      emulateFieldWatchpoint(debugProcess, context, event.location());
      return;
    }

    emulateFieldWatchpoint(debugProcess, context, event.location());
    MyRequestor requestor = new MyRequestor(getProject());
    addFieldWatchpoint(requestor, context, event);
    createMethodExitRequest(requestor, context, event);
  }

  private void processConstructorEntry(SuspendContextImpl context, LocatableEvent event) {
    DebugProcessImpl debugProcess = context.getDebugProcess();

    emulateFieldWatchpoint(debugProcess, context, event.location());

    if (!tryPopFrame(debugProcess, context)) {
      MyRequestor requestor = new MyRequestor(getProject());
      addFieldWatchpoint(requestor, context, event);
      createMethodExitRequest(requestor, context, event);
    }
  }

  private void processClassesAndInstancesInJVM(SuspendContextImpl context) {
    DebugProcessImpl debugProcess = context.getDebugProcess();
    VirtualMachineProxyImpl virtualMachineProxy = debugProcess.getVirtualMachineProxy();

    String clsName = getClassName();
    if (clsName == null || !virtualMachineProxy.canBeModified()) {
      return;
    }

    List<ReferenceType> classes = myClassesNames.keySet()
      .stream()
      .map(name -> virtualMachineProxy.classesByName(name))
      .flatMap(list -> list.stream())
      .collect(Collectors.toList());

    for (ReferenceType cls : classes) {
      Field field = cls.fieldByName(getFieldName());
      if (isStatic()) {
        Value value = cls.getValue(field);
        if (value != null) {
          captureFieldModification(value, null, false, debugProcess, context);
        }
      }
      else {
        for (ObjectReference instance : cls.instances(MAX_INSTANCES_NUMBER)) {
          Value value = instance.getValue(field);
          if (value != null) {
            captureFieldModification(value, instance, false, debugProcess, context);
          }
        }
      }
    }
  }

  private void addFieldWatchpoint(MyRequestor requestor, SuspendContextImpl context, LocatableEvent event) {
    Location location = event.location();
    DebugProcessImpl debugProcess = context.getDebugProcess();
    ReferenceType declaringType = location.declaringType();
    Field field = declaringType.fieldByName(getFieldName());

    ModificationWatchpointRequest request = debugProcess.getRequestsManager().createModificationWatchpointRequest(requestor, field);

    request.addClassFilter(declaringType);

    ObjectReference thisObj = getThisObject(context, event);
    if (thisObj != null) {
      request.addInstanceFilter(thisObj);
    }

    request.enable();
  }

  private void createRequestForSubclasses(DebugProcessImpl debugProcess, ReferenceType baseType) {
    VirtualMachineProxyImpl virtualMachineProxy = debugProcess.getVirtualMachineProxy();

    // create a request for classes that are already loaded
    if (virtualMachineProxy.canBeModified()) {
      virtualMachineProxy.allClasses()
        .stream()
        .filter(type -> DebuggerUtilsImpl.instanceOf(type, baseType) && !type.name().equals(baseType.name()))
        .forEach(derivedType -> createRequestForClass(debugProcess, derivedType));
    }

    // wait for the subclasses
    RequestManagerImpl requestManager = debugProcess.getRequestsManager();
    ClassPrepareRequest request = requestManager.createClassPrepareRequest((debuggerProcess, derivedType) -> {
      createRequestForClass(debugProcess, derivedType);
    }, null);
    if (request != null) {
      requestManager.registerRequest(this, request);
      request.addClassFilter(baseType);
      request.enable();
    }
  }

  @Override
  public Key<CollectionBreakpoint> getCategory() {
    return CATEGORY;
  }

  @Override
  public @Nullable String getClassName() {
    return getProperties().myClassName;
  }

  @Override
  public synchronized @NotNull Project getProject() {
    return super.getProject();
  }

  @Override
  public String getDisplayName() {
    return "";
  }

  public boolean isStatic() {
    return myIsStatic;
  }

  public synchronized String getFieldName() {
    return getProperties().myFieldName;
  }

  public boolean isPrivate() {
    return myIsPrivate;
  }

  private void createAllMethodsEntryRequest(DebugProcessImpl debugProcess) {
    RequestManagerImpl requestManager = debugProcess.getRequestsManager();
    MethodEntryRequest request = requestManager.createMethodEntryRequest(this);
    request.enable();
  }

  private void createMethodExitRequest(FilteredRequestor requestor, SuspendContextImpl context, LocatableEvent event) {
    DebugProcessImpl debugProcess = context.getDebugProcess();
    ReferenceType declaringType = event.location().declaringType();
    RequestManagerImpl requestManager = debugProcess.getRequestsManager();

    MethodExitRequest request = requestManager.createMethodExitRequest(requestor);

    request.addClassFilter(declaringType);
    ObjectReference thisObj = getThisObject(context, event);
    if (thisObj != null) {
      request.addInstanceFilter(thisObj);
    }
    ThreadReferenceProxyImpl threadReference = context.getThread();
    if (threadReference != null) {
      request.addThreadFilter(threadReference.getThreadReference());
    }

    request.enable();
  }

  private void createEmulatedMethodExitRequest(FilteredRequestor requestor, SuspendContextImpl context, LocatableEvent event) {
    DebugProcessImpl debugProcess = context.getDebugProcess();
    Method method = event.location().method();

    List<Location> allLineLocations = DebuggerUtilsEx.allLineLocations(method);

    if (method.isNative() || (allLineLocations == null && !method.isBridge())) {
      createMethodExitRequest(requestor, context, event);
    }
    else if (allLineLocations != null && !allLineLocations.isEmpty()) {
      visitMethodBytecode(method, allLineLocations, debugProcess, requestor);
    }
  }

  private void createMethodEntryRequest(DebugProcessImpl debugProcess, ReferenceType refType) {
    createAllMethodsEntryRequest(debugProcess);
  }

  private void captureFieldModification(Value valueToBe,
                                        Value obj,
                                        boolean shouldSaveStack,
                                        DebugProcessImpl debugProcess,
                                        SuspendContextImpl context) {
    StackFrameProxyImpl frameProxy = context.getFrameProxy();
    if (frameProxy == null) {
      return;
    }

    Value clsName = getInternalClsName(debugProcess, context);
    if (clsName == null) {
      return;
    }

    Value fieldName = frameProxy.getVirtualMachine().mirrorOf(getFieldName());
    Value shouldSave = frameProxy.getVirtualMachine().mirrorOf(shouldSaveStack);

    ArrayList<Value> args = new ArrayList<>();
    args.add(valueToBe);
    args.add(obj);
    args.add(clsName);
    args.add(fieldName);
    args.add(shouldSave);

    invokeInstrumentorMethod(debugProcess, context,
                             CAPTURE_FIELD_MODIFICATION_METHOD_NAME,
                             CAPTURE_FIELD_MODIFICATION_METHOD_DESCRIPTOR,
                             args);
  }

  private Value getInternalClsName(DebugProcessImpl debugProcess, SuspendContextImpl context) {
    String clsName = getClassName();
    String clsTypeDesc = myClassesNames.get(clsName);

    StackFrameProxyImpl frameProxy = context.getFrameProxy();

    if (clsTypeDesc == null || frameProxy == null) {
      return null;
    }

    Value clsTypeDescRef = frameProxy.getVirtualMachine().mirrorOf(clsTypeDesc);

    return invokeInstrumentorMethod(debugProcess, context,
                                    GET_INTERNAL_CLS_NAME_METHOD_NAME,
                                    GET_INTERNAL_CLS_NAME_METHOD_DESCRIPTOR,
                                    Collections.singletonList(clsTypeDescRef));
  }

  private void emulateFieldWatchpoint(DebugProcessImpl debugProcess, SuspendContextImpl context, Location location) {
    EvaluationContextImpl evalContext = new EvaluationContextImpl(context, context.getFrameProxy());
    evalContext = evalContext.withAutoLoadClasses(false);
    try {
      ClassType instrumentorCls = getInstrumentorClass(debugProcess, evalContext);
      if (instrumentorCls != null) {
        putFieldToCapture(debugProcess, context);
        transformClassesToEmulateFieldWatchpoint(debugProcess, context);
        if (suspendOnBreakpointHit()) {
          setLineBreakpoints(instrumentorCls, context);
        }
      }
    }
    catch (EvaluateException e) {
      DebuggerUtilsImpl.logError(e);
    }
  }

  private void putFieldToCapture(DebugProcessImpl debugProcess, SuspendContextImpl context) {
    StackFrameProxyImpl frameProxy = context.getFrameProxy();
    if (frameProxy == null) {
      return;
    }

    Value clsName = getInternalClsName(debugProcess, context);
    if (clsName == null) {
      return;
    }

    Value fieldName = frameProxy.getVirtualMachine().mirrorOf(getFieldName());

    invokeInstrumentorMethod(debugProcess, context, PUT_FIELD_TO_CAPTURE_METHOD_NAME,
                             PUT_FIELD_TO_CAPTURE_METHOD_DESCRIPTOR, List.of(clsName, fieldName));
  }

  private void transformClassesToEmulateFieldWatchpoint(DebugProcessImpl debugProcess,
                                                        SuspendContextImpl context) throws EvaluateException {
    StackFrameProxyImpl frameProxy = context.getFrameProxy();
    if (frameProxy == null) {
      return;
    }

    List<Value> args = ContainerUtil.map(myUnprocessedClasses.keySet(),
                                         clsName -> frameProxy.getVirtualMachine().mirrorOf(myUnprocessedClasses.get(clsName)));
    myUnprocessedClasses.clear();
    invokeInstrumentorMethod(debugProcess, context, EMULATE_FIELD_WATCHPOINT_METHOD_NAME,
                             EMULATE_FIELD_WATCHPOINT_METHOD_DESCRIPTOR, args);
  }

  private void setLineBreakpoints(ClassType instrumentorCls, SuspendContextImpl context) {
    List<Location> locations = findLocationsInInstrumentorMethods(instrumentorCls);
    for (Location location : locations) {
      SourcePosition position = locationToPosition(context.getDebugProcess(), location);
      MyLineBreakpoint breakpoint = new MyLineBreakpoint(location, position);
      breakpoint.createBreakpointRequest(context);
    }
  }

  private boolean suspendOnBreakpointHit() {
    return !DebuggerSettings.SUSPEND_NONE.equals(getSuspendPolicy());
  }

  private static Value invokeInstrumentorMethod(DebugProcessImpl debugProcess,
                                                SuspendContextImpl context,
                                                String methodName,
                                                String methodDesc,
                                                List<Value> args) {
    EvaluationContextImpl evalContext = new EvaluationContextImpl(context, context.getFrameProxy());
    evalContext = evalContext.withAutoLoadClasses(false);
    try {
      ClassType instrumentorCls = getInstrumentorClass(debugProcess, evalContext);
      if (instrumentorCls == null) {
        return null;
      }
      Method method = DebuggerUtils.findMethod(instrumentorCls, methodName, methodDesc);
      if (method != null) {
        return debugProcess.invokeMethod(evalContext, instrumentorCls, method, args);
      }
    }
    catch (EvaluateException e) {
      DebuggerUtilsImpl.logError(e);
    }
    return null;
  }

  private static boolean tryPopFrame(DebugProcessImpl debugProcess, SuspendContextImpl suspendContext) {
    StackFrameProxyImpl frameProxy = suspendContext.getFrameProxy();
    if (frameProxy == null) {
      return false;
    }
    try {
      frameProxy.threadProxy().popFrames(frameProxy);
      // debugProcess.getSuspendManager().popFrame(suspendContext);
      return true;
    }
    catch (final EvaluateException e) {
      return false;
    }
  }

  private static void visitMethodBytecode(Method method,
                                          List<Location> allLineLocations,
                                          DebugProcessImpl debugProcess,
                                          FilteredRequestor requestor) {
    MethodBytecodeUtil.visit(method, new MethodVisitor(Opcodes.API_VERSION) {
      int myLastLine = 0;

      @Override
      public void visitLineNumber(int line, Label start) {
        myLastLine = line;
      }

      @Override
      public void visitInsn(int opcode) {
        switch (opcode) {
          case Opcodes.RETURN:
          case Opcodes.IRETURN:
          case Opcodes.FRETURN:
          case Opcodes.ARETURN:
          case Opcodes.LRETURN:
          case Opcodes.DRETURN:
            //case Opcodes.ATHROW:
            allLineLocations.stream()
              .filter(l -> l.lineNumber() == myLastLine)
              .findFirst().ifPresent(location -> {
                createLocationBreakpointRequest(requestor, location, debugProcess, false);
              });
        }
      }
    }, true);
  }

  private static BreakpointRequest createLocationBreakpointRequest(@NotNull FilteredRequestor requestor,
                                                                   @Nullable Location location,
                                                                   @NotNull DebugProcessImpl debugProcess,
                                                                   boolean methodEntry) {
    return createLocationBreakpointRequest(requestor, location, debugProcess);
  }

  private static Location findLocationInDefaultMethod(ClassType instrumentorCls) {
    try {
      Method captureModificationCode =
        DebuggerUtils.findMethod(instrumentorCls,
                                 CAPTURE_COLLECTION_MODIFICATION_DEFAULT_METHOD_NAME,
                                 CAPTURE_COLLECTION_MODIFICATION_DEFAULT_METHOD_DESCRIPTOR);
      if (captureModificationCode != null) {
        List<Location> lines = captureModificationCode.allLineLocations();
        if (lines.size() >= 6) {
          return lines.get(5);
        }
      }
    }
    catch (AbsentInformationException e) {
      DebuggerUtilsImpl.logError(e);
    }
    return null;
  }

  private static Location findLocationInSpecialMethod(ClassType instrumentorCls) {
    try {
      Method captureModificationCode =
        DebuggerUtils.findMethod(instrumentorCls,
                                 CAPTURE_COLLECTION_MODIFICATION_SPECIAL_METHOD_NAME,
                                 CAPTURE_COLLECTION_MODIFICATION_SPECIAL_METHOD_DESCRIPTOR);
      if (captureModificationCode != null) {
        List<Location> lines = captureModificationCode.allLineLocations();
        if (lines.size() >= 3) {
          return lines.get(2);
        }
      }
    }
    catch (AbsentInformationException e) {
      DebuggerUtilsImpl.logError(e);
    }
    return null;
  }

  @NotNull
  private static List<Location> findLocationsInInstrumentorMethods(ClassType instrumentorCls) {
    List<Location> locations = new ArrayList<>();
    Location location = findLocationInDefaultMethod(instrumentorCls);
    if (location != null) {
      locations.add(location);
    }
    location = findLocationInSpecialMethod(instrumentorCls);
    if (location != null) {
      locations.add(location);
    }
    return locations;
  }

  private static @Nullable SourcePosition locationToPosition(DebugProcessImpl debugProcess, @Nullable Location location) {
    return location == null ? null : debugProcess.getPositionManager().getSourcePosition(location);
  }

  private static ClassType getInstrumentorClass(DebugProcessImpl debugProcess, @Nullable EvaluationContextImpl evalContext) {
    try {
      return (ClassType)debugProcess.findClass(evalContext, INSTRUMENTOR_CLASS, null);
    }
    catch (EvaluateException e) {
      return null;
    }
  }

  private class MyRequestor extends FilteredRequestorImpl {

    private MyRequestor(@NotNull Project project) {
      super(project);
    }

    @Override
    public boolean processLocatableEvent(@NotNull SuspendContextCommandImpl action, LocatableEvent event) throws EventProcessingException {
      SuspendContextImpl context = action.getSuspendContext();
      if (context == null) {
        return false;
      }
      DebugProcessImpl debugProcess = context.getDebugProcess();
      if (event instanceof ModificationWatchpointEvent) {
        Value valueToBe = ((ModificationWatchpointEvent)event).valueToBe();
        captureFieldModification(valueToBe, getThisObject(context, event), true, debugProcess, context);
      }
      else {
        debugProcess.getRequestsManager().deleteRequest(this);
        if (!myUnprocessedClasses.isEmpty()) {
          emulateFieldWatchpoint(debugProcess, context, event.location());
        }
      }
      return false;
    }
  }

  private class MyLineBreakpoint extends SyntheticLineBreakpoint {
    private final @Nullable SourcePosition myPosition;
    private final @Nullable Location myLocation;

    private MyLineBreakpoint(@Nullable Location location, @Nullable SourcePosition position) {
      super(CollectionBreakpoint.this.getProject());
      myLocation = location;
      myPosition = position;
      setSuspendPolicy(CollectionBreakpoint.this.getSuspendPolicy());
    }

    private void createBreakpointRequest(SuspendContextImpl suspendContext) {
      if (myLocation != null) {
        createLocationBreakpointRequest(this, myLocation, suspendContext.getDebugProcess());
      }
    }

    @Override
    public boolean processLocatableEvent(@NotNull SuspendContextCommandImpl action, LocatableEvent event) throws EventProcessingException {
      return processBreakpointHit(action, event);
    }

    private boolean processBreakpointHit(@NotNull SuspendContextCommandImpl action, LocatableEvent event) {
      SuspendContextImpl context = action.getSuspendContext();
      if (context == null) {
        return false;
      }
      try {
        DebugProcessImpl debugProcess = context.getDebugProcess();
        DebugProcessImpl.ResumeCommand stepOutCommand = debugProcess.createStepOutCommand(context);
        debugProcess.getManagerThread().schedule(stepOutCommand);
      }
      catch (Exception e) {
        DebuggerUtilsImpl.logError(e);
        return false;
      }
      return true;
    }

    @Override
    public @Nullable SourcePosition getSourcePosition() {
      return myPosition;
    }

    @Override
    public int getLineIndex() {
      return myPosition == null ? -1 : myPosition.getLine();
    }

    @Override
    public String getEventMessage(LocatableEvent event) {
      return "";
    }

    @Override
    protected String getFileName() {
      return "";
    }
  }
}