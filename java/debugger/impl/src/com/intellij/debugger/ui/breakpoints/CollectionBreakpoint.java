// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.CollectionBreakpointUtils;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.engine.requests.RequestManagerImpl;
import com.intellij.debugger.impl.DebuggerUtilsImpl;
import com.intellij.debugger.impl.PositionUtil;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.sun.jdi.*;
import com.sun.jdi.event.LocatableEvent;
import com.sun.jdi.event.ModificationWatchpointEvent;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.MethodEntryRequest;
import com.sun.jdi.request.MethodExitRequest;
import com.sun.jdi.request.ModificationWatchpointRequest;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.debugger.breakpoints.properties.JavaCollectionBreakpointProperties;

import javax.swing.*;
import java.util.*;
import java.util.stream.Collectors;

@ApiStatus.Experimental
public final class CollectionBreakpoint extends BreakpointWithHighlighter<JavaCollectionBreakpointProperties> {
  @NonNls public static final Key<CollectionBreakpoint> CATEGORY = BreakpointCategory.lookup("collection_breakpoints");

  private static final String GET_INTERNAL_CLS_NAME_METHOD_NAME = "getInternalClsName";
  private static final String GET_INTERNAL_CLS_NAME_METHOD_DESC = "(Ljava/lang/String;)Ljava/lang/String;";
  private static final String EMULATE_FIELD_WATCHPOINT_METHOD_NAME = "emulateFieldWatchpoint";
  private static final String EMULATE_FIELD_WATCHPOINT_METHOD_DESC = "([Ljava/lang/String;)V";
  private static final String PUT_FIELD_TO_CAPTURE_METHOD_NAME = "putFieldToCapture";
  private static final String PUT_FIELD_TO_CAPTURE_METHOD_DESC = "(Ljava/lang/String;Ljava/lang/String;)V";
  private static final String CAPTURE_FIELD_MODIFICATION_METHOD_NAME = "captureFieldModification";
  private static final String CAPTURE_FIELD_MODIFICATION_METHOD_DESC =
    "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Z)V";
  private static final String CAPTURE_COLLECTION_MODIFICATION_DEFAULT_METHOD_NAME = "captureCollectionModification";
  private static final String CAPTURE_COLLECTION_MODIFICATION_DEFAULT_METHOD_DESC =
    "(Lcom/intellij/rt/debugger/agent/CollectionBreakpointInstrumentor$Multiset;Ljava/lang/Object;)V";
  private static final String CAPTURE_COLLECTION_MODIFICATION_SPECIAL_METHOD_NAME = "captureCollectionModification";
  private static final String CAPTURE_COLLECTION_MODIFICATION_SPECIAL_METHOD_DESC = "(ZZLjava/lang/Object;Ljava/lang/Object;Z)V";

  private static final long MAX_INSTANCES_NUMBER = 1000000;
  private final Set<String> myUnprocessedClasses = new HashSet<>();
  private final Set<String> myClassesNames = new HashSet<>();
  private volatile boolean myClsPrepared = false;
  private volatile boolean myIsStatic = false;
  private volatile boolean myIsPrivate = false;
  private volatile boolean myIsFinal = false;

  private volatile boolean myAllMethodsEntryRequestIsEnabled = false;
  private String myClsTypeDesc = null;


  CollectionBreakpoint(Project project, XBreakpoint breakpoint) {
    super(project, breakpoint);
    initProperties();
  }

  @RequiresBackgroundThread
  @Override
  public void reload() {
    super.reload();
    initProperties();
  }

  private void initProperties() {
    PsiField field = PositionUtil.getPsiElementAt(myProject, PsiField.class, getSourcePosition());
    if (field != null) {
      getProperties().myFieldName = field.getName();
      PsiClass psiClass = field.getContainingClass();
      if (psiClass != null) {
        getProperties().myClassName = psiClass.getQualifiedName();
      }
      myIsPrivate = field.hasModifierProperty(PsiModifier.PRIVATE);
      myIsFinal = field.hasModifierProperty(PsiModifier.FINAL);
      myIsStatic = field.hasModifierProperty(PsiModifier.STATIC);
    }
    myClsPrepared = false;
    myAllMethodsEntryRequestIsEnabled = false;
  }

  @Override
  public void createRequestForPreparedClass(DebugProcessImpl debugProcess, ReferenceType refType) {
    if (myClsPrepared) {
      return;
    }
    setVariables(debugProcess);
    myClsTypeDesc = refType.signature();
    createRequestForClass(debugProcess, refType);
    if (!myIsFinal && !myIsPrivate) {
      createRequestForSubclasses(debugProcess, refType);
    }
    myClsPrepared = true;
  }

  @Override
  protected Icon getDisabledIcon(boolean isMuted) {
    if (DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager().findMasterBreakpoint(this) != null && isMuted) {
      return AllIcons.Debugger.Db_muted_dep_field_breakpoint;
    }
    return null;
  }

  @Override
  public String getEventMessage(LocatableEvent event) {
    return "";
  }

  @Override
  protected Icon getVerifiedIcon(boolean isMuted) {
    return isSuspend() ? AllIcons.Debugger.Db_verified_field_breakpoint : AllIcons.Debugger.Db_verified_no_suspend_field_breakpoint;
  }

  @Override
  protected Icon getVerifiedWarningsIcon(boolean isMuted) {
    return LayeredIcon.layeredIcon(new Icon[]{isMuted ? AllIcons.Debugger.Db_muted_field_breakpoint : AllIcons.Debugger.Db_field_breakpoint,
                               AllIcons.General.WarningDecorator});
  }

  @Override
  public boolean processLocatableEvent(@NotNull SuspendContextCommandImpl action, LocatableEvent event) throws EventProcessingException {
    SuspendContextImpl context = action.getSuspendContext();
    if (context == null) {
      return false;
    }

    final @NotNull DebugProcessImpl debugProcess = context.getDebugProcess();

    debugProcess.getRequestsManager().deleteRequest(this); // delete method entry request
    myAllMethodsEntryRequestIsEnabled = false;

    Location location = event.location();
    if (location == null) {
      emulateFieldWatchpoint(debugProcess, context);
      return false;
    }

    Method method = location.method();
    String type = location.declaringType().name();

    MethodEntryPlace place = MethodEntryPlace.DEFAULT;
    if (method.isStaticInitializer() && myClassesNames.contains(type)) {
      place = MethodEntryPlace.STATIC_BLOCK;
    }
    else if (method.isConstructor() && myClassesNames.contains(type)) {
      place = MethodEntryPlace.CONSTRUCTOR;
    }

    if (myIsStatic) {
      processClassesInJVM(context, event, place);
    }
    else {
      processInstancesInJVM(context, event, place);
    }

    emulateFieldWatchpoint(debugProcess, context);

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

  private void setVariables(DebugProcessImpl debugProcess) {
    CollectionBreakpointUtils.setCollectionHistorySavingEnabled(debugProcess, shouldSaveCollectionHistory());
  }

  private boolean shouldSaveCollectionHistory() {
    return getProperties().SHOULD_SAVE_COLLECTION_HISTORY;
  }

  private void createRequestForClass(DebugProcessImpl debugProcess, ReferenceType refType) {
    String clsName = refType.name();
    myClassesNames.add(clsName);
    myUnprocessedClasses.add(clsName);
    if (!myAllMethodsEntryRequestIsEnabled) {
      createAllMethodsEntryRequest(debugProcess);
    }
  }

  private void processConstructorEntry(SuspendContextImpl context, LocatableEvent event) {
    if (!tryPopFrame(context)) {
      Location location = event.location();
      ReferenceType declaringType = location.declaringType();
      ObjectReference thisObj = getThisObject(context, event);
      setTemporaryFieldWatchpoint(context, declaringType, thisObj, context.getThread());
    }
  }

  private void setTemporaryFieldWatchpoint(@NotNull SuspendContextImpl context,
                                           @NotNull ReferenceType declaringType,
                                           @Nullable ObjectReference thisObj,
                                           @Nullable ThreadReferenceProxyImpl thread) {
    MyRequestor requestor = new MyRequestor(getProject());
    addFieldWatchpoint(requestor, context, declaringType, thisObj);
    createMethodExitRequest(requestor, context, declaringType, thisObj, thread);
  }

  private List<ReferenceType> getTrackedClassesInJVM(SuspendContextImpl context) {
    VirtualMachineProxyImpl virtualMachineProxy = context.getVirtualMachineProxy();

    return myClassesNames
      .stream()
      .map(name -> virtualMachineProxy.classesByName(name))
      .flatMap(list -> list.stream())
      .filter(cls -> cls.isPrepared())
      .collect(Collectors.toList());
  }

  private List<ObjectReference> getTrackedInstancesInJVM(SuspendContextImpl context) {
    return getTrackedClassesInJVM(context)
      .stream()
      .map(cls -> cls.instances(MAX_INSTANCES_NUMBER))
      .flatMap(list -> list.stream())
      .collect(Collectors.toList());
  }

  private void processInstancesInJVM(SuspendContextImpl context, LocatableEvent event, MethodEntryPlace place) {
    List<ObjectReference> instances = getTrackedInstancesInJVM(context);
    if (instances.isEmpty()) {
      return;
    }

    if (instances.size() == 1 && MethodEntryPlace.CONSTRUCTOR.equals(place)) {
      processConstructorEntry(context, event);
    }
    else {
      processAllInstances(context, instances);
    }
  }

  private void processClassesInJVM(SuspendContextImpl context, LocatableEvent event, MethodEntryPlace place) {
    List<ReferenceType> classes = getTrackedClassesInJVM(context);
    if (classes.isEmpty()) {
      return;
    }

    if (classes.size() == 1 && MethodEntryPlace.STATIC_BLOCK.equals(place)) {
      ReferenceType declaringType = event.location().declaringType();
      ThreadReferenceProxyImpl thread = context.getThread();
      setTemporaryFieldWatchpoint(context, declaringType, null, thread);
    }
    else {
      processAllClasses(context, classes);
    }
  }

  private void processAllClasses(SuspendContextImpl context, List<ReferenceType> classes) {
    String fieldName = getFieldName();
    for (ReferenceType cls : classes) {
      Field field = DebuggerUtils.findField(cls, fieldName);
      if (cls.isInitialized()) {
        captureClsField(cls, field, context.getDebugProcess(), context);
      }
    }

    VirtualMachineProxyImpl vm = context.getVirtualMachineProxy();

    for (ThreadReferenceProxyImpl thread : vm.allThreads()) {
      try {
        if (thread.isSuspended()) {
          processMethodEntryInAllFrames(thread, context, classes);
        }
      }
      catch (EvaluateException e) {
        DebuggerUtilsImpl.logError(e);
      }
    }
  }

  private void processMethodEntryInAllFrames(ThreadReferenceProxyImpl thread,
                                             SuspendContextImpl context,
                                             List<ReferenceType> classes) throws EvaluateException {
    Set<ReferenceType> classesCopy = new HashSet<>(classes);
    List<StackFrameProxyImpl> frames = thread.frames();
    for (StackFrameProxyImpl frame : frames) {
      Method method = frame.location().method();
      ReferenceType declaringType = method.declaringType();
      boolean shouldCapture = !myIsFinal || method.isStaticInitializer();
      if (shouldCapture && classesCopy.contains(declaringType)) {
        ObjectReference thisObject = frame.thisObject();
        setTemporaryFieldWatchpoint(context, declaringType, thisObject, thread);
        classesCopy.remove(declaringType);
      }
    }
  }

  private void processAllInstances(SuspendContextImpl context, List<ObjectReference> instances) {
    String fieldName = getFieldName();
    for (ObjectReference instance : instances) {
      Field field = DebuggerUtils.findField(instance.referenceType(), fieldName);
      captureInstanceField(instance, field, context.getDebugProcess(), context);
    }

    VirtualMachineProxyImpl vm = context.getVirtualMachineProxy();

    for (ThreadReferenceProxyImpl thread : vm.allThreads()) {
      try {
        if (thread.isSuspended()) {
          processNonStaticMethodEntryInAllFrames(thread, context, instances);
        }
      }
      catch (EvaluateException e) {
        DebuggerUtilsImpl.logError(e);
      }
    }
  }

  private void processNonStaticMethodEntryInAllFrames(ThreadReferenceProxyImpl thread,
                                                      SuspendContextImpl context,
                                                      List<ObjectReference> instances) throws EvaluateException {
    Set<ObjectReference> instancesCopy = new HashSet<>(instances);
    List<StackFrameProxyImpl> frames = thread.frames();
    for (StackFrameProxyImpl frame : frames) {
      Method method = frame.location().method();
      ObjectReference thisObject = frame.thisObject();
      boolean shouldCapture = !myIsFinal || method.isConstructor();
      if (shouldCapture && !method.isStatic() && instancesCopy.contains(thisObject)) {
        setTemporaryFieldWatchpoint(context, method.declaringType(), thisObject, thread);
        instancesCopy.remove(thisObject);
      }
    }
  }

  private void captureClsField(ReferenceType cls,
                               Field field,
                               DebugProcessImpl debugProcess,
                               SuspendContextImpl context) {
    Value value = cls.getValue(field);
    if (value != null) {
      captureFieldModification(value, null, false, debugProcess, context);
    }
  }

  private void captureInstanceField(ObjectReference instance,
                                    Field field,
                                    DebugProcessImpl debugProcess,
                                    SuspendContextImpl context) {
    Value value = instance.getValue(field);
    if (value != null) {
      captureFieldModification(value, instance, false, debugProcess, context);
    }
  }

  private void addFieldWatchpoint(MyRequestor requestor,
                                  SuspendContextImpl context,
                                  ReferenceType declaringType,
                                  @Nullable ObjectReference thisObj) {
    DebugProcessImpl debugProcess = context.getDebugProcess();
    Field field = DebuggerUtils.findField(declaringType, getFieldName());

    ModificationWatchpointRequest request =
      debugProcess.getRequestsManager().createModificationWatchpointRequest(requestor, field);

    request.addClassFilter(declaringType);

    if (thisObj != null) {
      request.addInstanceFilter(thisObj);
    }

    request.enable();
  }

  private void createRequestForSubclasses(DebugProcessImpl debugProcess, @NotNull ReferenceType baseType) {
    // create a request for classes that are already loaded
    baseType.virtualMachine().allClasses()
      .stream()
      .filter(type -> DebuggerUtilsImpl.instanceOf(type, baseType) && !type.name().equals(baseType.name()))
      .forEach(derivedType -> createRequestForClass(debugProcess, derivedType));

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

  public synchronized String getFieldName() {
    return getProperties().myFieldName;
  }

  private void createAllMethodsEntryRequest(DebugProcessImpl debugProcess) {
    RequestManagerImpl requestManager = debugProcess.getRequestsManager();
    MethodEntryRequest request = requestManager.createMethodEntryRequest(this);
    request.enable();
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

    Value internalClsName = getInternalClsName(debugProcess, context);
    if (internalClsName == null) {
      return;
    }

    Value fieldName = frameProxy.getVirtualMachine().mirrorOf(getFieldName());
    Value shouldSave = frameProxy.getVirtualMachine().mirrorOf(shouldSaveStack);

    ArrayList<Value> args = new ArrayList<>();
    args.add(valueToBe);
    args.add(obj);
    args.add(internalClsName);
    args.add(fieldName);
    args.add(shouldSave);

    CollectionBreakpointUtils.invokeInstrumentorMethod(debugProcess, context,
                                                       CAPTURE_FIELD_MODIFICATION_METHOD_NAME,
                                                       CAPTURE_FIELD_MODIFICATION_METHOD_DESC,
                                                       args);
  }

  private Value getInternalClsName(DebugProcessImpl debugProcess, SuspendContextImpl context) {
    String clsTypeDesc = myClsTypeDesc;
    StackFrameProxyImpl frameProxy = context.getFrameProxy();

    if (clsTypeDesc == null || frameProxy == null) {
      return null;
    }

    Value clsTypeDescRef = frameProxy.getVirtualMachine().mirrorOf(clsTypeDesc);

    return CollectionBreakpointUtils.invokeInstrumentorMethod(debugProcess, context,
                                                              GET_INTERNAL_CLS_NAME_METHOD_NAME,
                                                              GET_INTERNAL_CLS_NAME_METHOD_DESC,
                                                              Collections.singletonList(clsTypeDescRef));
  }

  private void emulateFieldWatchpoint(DebugProcessImpl debugProcess, SuspendContextImpl context) {
    try {
      putFieldToCapture(debugProcess, context);
      transformClassesToEmulateFieldWatchpoint(debugProcess, context);
      if (suspendOnBreakpointHit()) {
        setLineBreakpoints(context);
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

    String clsTypeDesc = myClsTypeDesc;
    if (clsTypeDesc == null) {
      return;
    }

    Value clsTypeDescRef = frameProxy.getVirtualMachine().mirrorOf(clsTypeDesc);
    Value fieldName = frameProxy.getVirtualMachine().mirrorOf(getFieldName());

    CollectionBreakpointUtils.invokeInstrumentorMethod(debugProcess, context, PUT_FIELD_TO_CAPTURE_METHOD_NAME,
                                                       PUT_FIELD_TO_CAPTURE_METHOD_DESC, List.of(clsTypeDescRef, fieldName));
  }

  private void transformClassesToEmulateFieldWatchpoint(DebugProcessImpl debugProcess,
                                                        SuspendContextImpl context) throws EvaluateException {
    StackFrameProxyImpl frameProxy = context.getFrameProxy();
    if (frameProxy == null) {
      return;
    }

    List<Value> args = ContainerUtil.map(myUnprocessedClasses, clsName -> frameProxy.getVirtualMachine().mirrorOf(clsName));
    myUnprocessedClasses.clear();
    CollectionBreakpointUtils.invokeInstrumentorMethod(debugProcess, context, EMULATE_FIELD_WATCHPOINT_METHOD_NAME,
                                                       EMULATE_FIELD_WATCHPOINT_METHOD_DESC, args);
  }

  private void setLineBreakpoints(SuspendContextImpl context) {
    DebugProcessImpl debugProcess = context.getDebugProcess();
    EvaluationContextImpl evalContext = new EvaluationContextImpl(context, context.getFrameProxy());
    evalContext = evalContext.withAutoLoadClasses(false);
    ClassType instrumentorCls = CollectionBreakpointUtils.getInstrumentorClass(debugProcess, evalContext);
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

  private static void createMethodExitRequest(FilteredRequestor requestor,
                                              SuspendContextImpl context,
                                              @NotNull ReferenceType declaringType,
                                              @Nullable ObjectReference thisObj,
                                              @Nullable ThreadReferenceProxyImpl thread) {
    DebugProcessImpl debugProcess = context.getDebugProcess();
    RequestManagerImpl requestManager = debugProcess.getRequestsManager();

    MethodExitRequest request = requestManager.createMethodExitRequest(requestor);

    request.addClassFilter(declaringType);

    if (thisObj != null) {
      request.addInstanceFilter(thisObj);
    }

    if (thread != null) {
      request.addThreadFilter(thread.getThreadReference());
    }

    request.enable();
  }

  private static boolean tryPopFrame(SuspendContextImpl suspendContext) {
    StackFrameProxyImpl frameProxy = suspendContext.getFrameProxy();
    if (frameProxy == null) {
      return false;
    }
    try {
      frameProxy.threadProxy().popFrames(frameProxy);
      return true;
    }
    catch (final EvaluateException e) {
      return false;
    }
  }

   /*

   private static void createEmulatedMethodExitRequest(FilteredRequestor requestor, SuspendContextImpl context, LocatableEvent event) {
    DebugProcessImpl debugProcess = context.getDebugProcess();
    Method method = event.location().method();

    List<Location> allLineLocations = DebuggerUtilsEx.allLineLocations(method);

    if (method.isNative() || (allLineLocations == null && !method.isBridge())) {
      createMethodExitRequest(requestor, context, event.location().declaringType(), null, null);
    }
    else if (allLineLocations != null && !allLineLocations.isEmpty()) {
      visitMethodBytecode(method, allLineLocations, debugProcess, requestor);
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

  private static void createLocationBreakpointRequest(@NotNull FilteredRequestor requestor,
                                                      @Nullable Location location,
                                                      @NotNull DebugProcessImpl debugProcess,
                                                      boolean methodEntry) {
    createLocationBreakpointRequest(requestor, location, debugProcess);
  }

  */

  private static Location findLocationInMethod(ClassType instrumentorCls, String methodName, String methodDesc, int lineNumber) {
    try {
      Method method =
        DebuggerUtils.findMethod(instrumentorCls, methodName, methodDesc);
      if (method != null) {
        List<Location> lines = method.allLineLocations();
        if (lines.size() >= lineNumber + 1) {
          return lines.get(lineNumber);
        }
      }
    }
    catch (AbsentInformationException e) {
      DebuggerUtilsImpl.logError(e);
    }
    return null;
  }

  private static Location findLocationInCaptureFieldModificationMethod(ClassType instrumentorCls) {
    return findLocationInMethod(instrumentorCls,
                                CAPTURE_FIELD_MODIFICATION_METHOD_NAME,
                                CAPTURE_FIELD_MODIFICATION_METHOD_DESC,
                                5);
  }

  private static Location findLocationInDefaultCaptureCollectionModificationMethod(ClassType instrumentorCls) {
    return findLocationInMethod(instrumentorCls,
                                CAPTURE_COLLECTION_MODIFICATION_DEFAULT_METHOD_NAME,
                                CAPTURE_COLLECTION_MODIFICATION_DEFAULT_METHOD_DESC,
                                5);
  }

  private static Location findLocationInSpecialCaptureCollectionModificationMethod(ClassType instrumentorCls) {
    return findLocationInMethod(instrumentorCls,
                                CAPTURE_COLLECTION_MODIFICATION_SPECIAL_METHOD_NAME,
                                CAPTURE_COLLECTION_MODIFICATION_SPECIAL_METHOD_DESC,
                                2);
  }

  @NotNull
  private static List<Location> findLocationsInInstrumentorMethods(ClassType instrumentorCls) {
    List<Location> locations = new ArrayList<>();
    Location location = findLocationInCaptureFieldModificationMethod(instrumentorCls);
    if (location != null) {
      locations.add(location);
    }
    location = findLocationInDefaultCaptureCollectionModificationMethod(instrumentorCls);
    if (location != null) {
      locations.add(location);
    }
    location = findLocationInSpecialCaptureCollectionModificationMethod(instrumentorCls);
    if (location != null) {
      locations.add(location);
    }
    return locations;
  }

  private static @Nullable SourcePosition locationToPosition(DebugProcessImpl debugProcess, @Nullable Location location) {
    return location == null ? null : debugProcess.getPositionManager().getSourcePosition(location);
  }

  private static boolean stackContainsAnyObsoleteMethod(SuspendContextImpl context,
                                                        ReferenceType declaringType,
                                                        ObjectReference thisObj) {
    ThreadReferenceProxyImpl thread = context.getThread();
    if (thread == null) {
      return false;
    }
    try {
      List<StackFrameProxyImpl> frames = thread.frames();
      if (frames.size() == 1) {
        return false;
      }
      for (StackFrameProxyImpl frame : frames.subList(1, frames.size())) {
        Method method = frame.location().method();
        if (method.isObsolete() && method.declaringType().equals(declaringType)) {
          return thisObj == null || thisObj.equals(frame.thisObject());
        }
      }
    }
    catch (EvaluateException e) {
      DebuggerUtilsImpl.logError(e);
    }
    return false;
  }

  private enum MethodEntryPlace {
    STATIC_BLOCK,
    CONSTRUCTOR,
    DEFAULT
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
      ObjectReference thisObj = getThisObject(context, event);

      if (event instanceof ModificationWatchpointEvent) {
        Value valueToBe = ((ModificationWatchpointEvent)event).valueToBe();
        captureFieldModification(valueToBe, thisObj, true, debugProcess, context);
      }
      else {
        ReferenceType declaringType = event.location().declaringType();
        if (!stackContainsAnyObsoleteMethod(context, declaringType, thisObj)) {
          debugProcess.getRequestsManager().deleteRequest(this);
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
      return processBreakpointHit(action);
    }

    private boolean processBreakpointHit(@NotNull SuspendContextCommandImpl action) {
      SuspendContextImpl context = action.getSuspendContext();
      if (context == null) {
        return false;
      }
      try {
        DebugProcessImpl debugProcess = context.getDebugProcess();
        DebugProcessImpl.ResumeCommand stepOutCommand = debugProcess.createStepOutCommand(context);
        context.getManagerThread().schedule(stepOutCommand);
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