// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.memory.agent;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.JVMNameUtil;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.ClassLoadingUtils;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.memory.agent.extractor.ProxyExtractor;
import com.intellij.debugger.memory.agent.parsers.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.system.CpuArch;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JdkVersionDetector;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class IdeaNativeAgentProxyMirror {
  private static final Logger LOG = Logger.getInstance(IdeaNativeAgentProxyMirror.class);

  private static final String PROXY_CLASS_NAME = "com.intellij.memory.agent.IdeaNativeAgentProxy";
  
  private static final String IS_LOADED = "isLoaded";

  private static final String CAN_ESTIMATE_OBJECT_SIZE = "canEstimateObjectSize";

  private static final String CAN_ESTIMATE_OBJECTS_SIZES = "canEstimateObjectsSizes";

  private static final String CAN_GET_SHALLOW_SIZE_BY_CLASSES = "canGetShallowSizeByClasses";
  private static final String CAN_GET_RETAINED_SIZE_BY_CLASSES = "canGetRetainedSizeByClasses";
  private static final String CAN_FIND_PATHS_TO_CLOSEST_GC_ROOTS = "canFindPathsToClosestGcRoots";
  private static final String ESTIMATE_OBJECT_SIZE = "size";

  private static final String ESTIMATE_OBJECTS_SIZES = "estimateRetainedSize";
  private static final String FIND_PATHS_TO_CLOSEST_GC_ROOTS = "findPathsToClosestGcRoots";
  private static final String GET_SHALLOW_SIZE_BY_CLASSES = "getShallowSizeByClasses";
  private static final String GET_RETAINED_SIZE_BY_CLASSES = "getRetainedSizeByClasses";
  private static final String GET_SHALLOW_AND_RETAINED_SIZE_BY_CLASSES = "getShallowAndRetainedSizeByClasses";
  private static final String PROXY_CONSTRUCTOR_SIGNATURE = "(Ljava/lang/Object;Ljava/lang/Object;J)V";

  private final String myCancellationFileName;
  private final String myProgressFileName;
  private ClassType myProxyType = null;

  public IdeaNativeAgentProxyMirror(@NotNull String cancellationFileName, @NotNull String progressFileName) {
    this.myCancellationFileName = cancellationFileName;
    this.myProgressFileName = progressFileName;
  }

  public String getCancellationFileName() {
    return myCancellationFileName;
  }

  public String getProgressFileName() {
    return myProgressFileName;
  }

  @NotNull
  public MemoryAgentActionResult<Pair<long[], ObjectReference[]>> estimateObjectSize(@NotNull EvaluationContextImpl evaluationContext,
                                                                                     @NotNull ObjectReference reference,
                                                                                     long timeoutInMillis) throws EvaluateException {
    Value result = callMethod(
      evaluationContext,
      ESTIMATE_OBJECT_SIZE,
      timeoutInMillis, reference
    );
    Pair<MemoryAgentActionResult.ErrorCode, Value> errCodeAndResult = ErrorCodeParser.INSTANCE.parse(result);
    MemoryAgentActionResult.ErrorCode errCode = errCodeAndResult.getFirst();
    Pair<long[], ObjectReference[]> sizesAndObjects;
    if (errCode != MemoryAgentActionResult.ErrorCode.OK) {
      sizesAndObjects = new Pair<>(new long[0], new ObjectReference[0]);
    } else {
      Pair<Long[], ObjectReference[]> parsingResult = SizeAndHeldObjectsParser.INSTANCE.parse(errCodeAndResult.getSecond());
      sizesAndObjects = new Pair<>(
        Arrays.stream(parsingResult.getFirst()).mapToLong(Long::longValue).toArray(),
        parsingResult.getSecond()
      );
    }

    return new MemoryAgentActionResult<>(sizesAndObjects, errCode);
  }

  @NotNull
  public MemoryAgentActionResult<long[]> estimateObjectsSizes(@NotNull EvaluationContextImpl evaluationContext,
                                                              @NotNull List<ObjectReference> references,
                                                              long timeoutInMillis) throws EvaluateException {
    ArrayReference array = wrapWithArray(evaluationContext, references);
    Value result = callMethod(
      evaluationContext,
      ESTIMATE_OBJECTS_SIZES,
      timeoutInMillis, array
    );
    Pair<MemoryAgentActionResult.ErrorCode, Value> errCodeAndResult = ErrorCodeParser.INSTANCE.parse(result);
    return new MemoryAgentActionResult<>(
      LongArrayParser.INSTANCE.parse(errCodeAndResult.getSecond()).stream().mapToLong(Long::longValue).toArray(),
      errCodeAndResult.getFirst()
    );
  }

  @NotNull
  public MemoryAgentActionResult<long[]> getShallowSizeByClasses(@NotNull EvaluationContextImpl evaluationContext,
                                                                 @NotNull List<ReferenceType> classes,
                                                                 long timeoutInMillis) throws EvaluateException {
    ArrayReference array = wrapWithArray(evaluationContext, ContainerUtil.map(classes, ReferenceType::classObject));
    Value result = callMethod(
      evaluationContext,
      GET_SHALLOW_SIZE_BY_CLASSES,
      timeoutInMillis, array
    );
    Pair<MemoryAgentActionResult.ErrorCode, Value> errCodeAndResult = ErrorCodeParser.INSTANCE.parse(result);
    return new MemoryAgentActionResult<>(
      LongArrayParser.INSTANCE.parse(errCodeAndResult.getSecond()).stream().mapToLong(Long::longValue).toArray(),
      errCodeAndResult.getFirst()
    );
  }

  @NotNull
  public MemoryAgentActionResult<long[]> getRetainedSizeByClasses(@NotNull EvaluationContextImpl evaluationContext,
                                                                  @NotNull List<ReferenceType> classes,
                                                                  long timeoutInMillis) throws EvaluateException {
    ArrayReference array = wrapWithArray(evaluationContext, ContainerUtil.map(classes, ReferenceType::classObject));
    Value result = callMethod(
      evaluationContext,
      GET_RETAINED_SIZE_BY_CLASSES,
      timeoutInMillis, array
    );
    Pair<MemoryAgentActionResult.ErrorCode, Value> errCodeAndResult = ErrorCodeParser.INSTANCE.parse(result);
    return new MemoryAgentActionResult<>(
      LongArrayParser.INSTANCE.parse(errCodeAndResult.getSecond()).stream().mapToLong(Long::longValue).toArray(),
      errCodeAndResult.getFirst()
    );
  }

  @NotNull
  public MemoryAgentActionResult<Pair<long[], long[]>> getShallowAndRetainedSizeByClasses(@NotNull EvaluationContextImpl evaluationContext,
                                                                                          @NotNull List<ReferenceType> classes,
                                                                                          long timeoutInMillis) throws EvaluateException {
    ArrayReference array = wrapWithArray(evaluationContext, ContainerUtil.map(classes, ReferenceType::classObject));
    Value result = callMethod(
      evaluationContext,
      GET_SHALLOW_AND_RETAINED_SIZE_BY_CLASSES,
      timeoutInMillis, array
    );
    Pair<MemoryAgentActionResult.ErrorCode, Value> errCodeAndResult = ErrorCodeParser.INSTANCE.parse(result);
    Pair<List<Long>, List<Long>> shallowAndRetainedSizes = ShallowAndRetainedSizeParser.INSTANCE.parse(errCodeAndResult.getSecond());
    return new MemoryAgentActionResult<>(
      new Pair<>(
        shallowAndRetainedSizes.getFirst().stream().mapToLong(Long::longValue).toArray(),
        shallowAndRetainedSizes.getSecond().stream().mapToLong(Long::longValue).toArray()
      ),
      errCodeAndResult.getFirst()
    );
  }

  @NotNull
  public MemoryAgentActionResult<ReferringObjectsInfo> findPathsToClosestGCRoots(@NotNull EvaluationContextImpl evaluationContext,
                                                                                 @NotNull ObjectReference reference,
                                                                                 int pathsNumber, int objectsNumber,
                                                                                 long timeoutInMillis) throws EvaluateException {
    IntegerValue pathsNumberValue = evaluationContext.getDebugProcess().getVirtualMachineProxy().mirrorOf(pathsNumber);
    IntegerValue objectsNumberValue = evaluationContext.getDebugProcess().getVirtualMachineProxy().mirrorOf(objectsNumber);
    Value result = callMethod(
      evaluationContext,
      FIND_PATHS_TO_CLOSEST_GC_ROOTS,
      timeoutInMillis, reference, pathsNumberValue, objectsNumberValue
    );

    Pair<MemoryAgentActionResult.ErrorCode, Value> errCodeAndResult = ErrorCodeParser.INSTANCE.parse(result);
    MemoryAgentActionResult.ErrorCode errCode = errCodeAndResult.getFirst();
    ReferringObjectsInfo returnValue;
    if (errCode != MemoryAgentActionResult.ErrorCode.OK) {
      returnValue = new ReferringObjectsInfo(
        Collections.singletonList(reference),
        Collections.singletonList(
          Collections.singletonList(new CalculationTimeoutReferringObject())
        )
      );
    } else {
      returnValue = GcRootsPathsParser.INSTANCE.parse(errCodeAndResult.getSecond());
    }

    return new MemoryAgentActionResult<>(returnValue, errCodeAndResult.getFirst());
  }

  public MemoryAgentCapabilities initializeCapabilities(@NotNull EvaluationContextImpl context) throws EvaluateException {
    ClassType proxyType = getProxyType(context);
    boolean isAgentLoaded = checkAgentCapability(context, proxyType, IS_LOADED);
    if (!isAgentLoaded) {
      return MemoryAgentCapabilities.DISABLED;
    }
    else {
      MemoryAgentCapabilities.Builder builder = new MemoryAgentCapabilities.Builder();
      return builder
        .setCanEstimateObjectSize(checkAgentCapability(context, proxyType, CAN_ESTIMATE_OBJECT_SIZE))
        .setCanEstimateObjectsSizes(checkAgentCapability(context, proxyType, CAN_ESTIMATE_OBJECTS_SIZES))
        .setCanGetShallowSizeByClasses(checkAgentCapability(context, proxyType, CAN_GET_SHALLOW_SIZE_BY_CLASSES))
        .setCanGetRetainedSizeByClasses(checkAgentCapability(context, proxyType, CAN_GET_RETAINED_SIZE_BY_CLASSES))
        .setCanFindPathsToClosestGcRoots(checkAgentCapability(context, proxyType, CAN_FIND_PATHS_TO_CLOSEST_GC_ROOTS))
        .buildLoaded();
    }
  }

  @Nullable
  public MemoryAgentProgressPoint checkProgress() {
    if (!FileUtil.exists(myProgressFileName)) {
      return null;
    }

    try {
      return MemoryAgentProgressPoint.fromJson(myProgressFileName);
    }
    catch (IOException ex) {
      LOG.error("Failed to read progress point from file", ex);
      return null;
    }
    catch (Exception ex) {
      LOG.error("Failed to create valid progress point class", ex);
      return null;
    }
  }

  @NotNull
  private ClassType getProxyType(@NotNull EvaluationContextImpl evaluationContext) throws EvaluateException {
    if (myProxyType == null) {
      boolean valueBefore = evaluationContext.isAutoLoadClasses();
      try {
        return getOrLoadProxyType(evaluationContext);
      }
      finally {
        evaluationContext.setAutoLoadClasses(valueBefore);
      }
    }
    return myProxyType;
  }

  @NotNull
  private static ObjectReference getProxyInstance(@NotNull EvaluationContextImpl evaluationContext,
                                                  @NotNull ClassType proxyType,
                                                  @NotNull ObjectReference cancellationFileName,
                                                  @NotNull ObjectReference progressFileName,
                                                  @NotNull LongValue timeoutInMillis) throws EvaluateException {
    DebugProcessImpl debugProcess = evaluationContext.getDebugProcess();
    Method constructor = DebuggerUtils.findMethod(proxyType, JVMNameUtil.CONSTRUCTOR_NAME, PROXY_CONSTRUCTOR_SIGNATURE);
    if (constructor == null) {
      throw EvaluateExceptionUtil.createEvaluateException("No appropriate constructor found for proxy class");
    }
    return debugProcess.newInstance(
      evaluationContext, proxyType, constructor, Arrays.asList(cancellationFileName, progressFileName, timeoutInMillis)
    );
  }

  private ClassType getOrLoadProxyType(@NotNull EvaluationContextImpl evaluationContext) throws EvaluateException {
    ClassObjectReference classObjectReference = evaluationContext.computeAndKeep(() -> {
      long start = System.currentTimeMillis();
      ReferenceType referenceType = loadUtilityClass(evaluationContext);
      if (referenceType == null) {
        throw EvaluateExceptionUtil.createEvaluateException("Could not load memory agent proxy class");
      }
      long duration = System.currentTimeMillis() - start;
      LOG.info("Loading of agent proxy class took " + duration + " ms");

      return referenceType.classObject();
    });
    myProxyType = (ClassType)classObjectReference.reflectedType();
    return myProxyType;
  }

  private static boolean checkAgentCapability(@NotNull EvaluationContextImpl evaluationContext,
                                              @NotNull ClassType proxyType,
                                              @NotNull String capabilityMethodName) {
    try {
      Value value = callMethod(evaluationContext, proxyType, capabilityMethodName, Collections.emptyList());
      return BooleanParser.INSTANCE.parse(value);
    }
    catch (EvaluateException e) {
      LOG.warn("Exception while capability checking: ", e);
    }
    return false;
  }

  @NotNull
  private static LongValue getLongValue(@NotNull EvaluationContextImpl evaluationContext, long timeoutInMillis) {
    return evaluationContext.getDebugProcess().getVirtualMachineProxy().mirrorOf(timeoutInMillis);
  }

  @NotNull
  private static StringReference getStringReference(@NotNull EvaluationContextImpl evaluationContext, @NotNull String string)
    throws EvaluateException {
    return DebuggerUtilsEx.mirrorOfString(string, evaluationContext.getDebugProcess().getVirtualMachineProxy(), evaluationContext);
  }

  @NotNull
  private static Value callMethod(@NotNull EvaluationContextImpl evaluationContext,
                                  @NotNull ClassType proxyType,
                                  @NotNull String methodName,
                                  @NotNull List<? extends Value> args) throws EvaluateException {
    return callMethod(
      evaluationContext,
      proxyType,
      methodName,
      getStringReference(evaluationContext, ""),
      getStringReference(evaluationContext, ""),
      getLongValue(evaluationContext, -1),
      args
    );
  }

  @NotNull
  public Value callMethod(@NotNull EvaluationContextImpl evaluationContext,
                          @NotNull String methodName,
                          long timeoutInMillis,
                          Value... args) throws EvaluateException {
    ClassType proxyType = getProxyType(evaluationContext);
    return callMethod(
      evaluationContext,
      proxyType,
      methodName,
      getStringReference(evaluationContext, myCancellationFileName),
      getStringReference(evaluationContext, myProgressFileName),
      getLongValue(evaluationContext, timeoutInMillis),
      Arrays.asList(args)
    );
  }

  @NotNull
  private static Value callMethod(@NotNull EvaluationContextImpl evaluationContext,
                                  @NotNull ClassType proxyType,
                                  @NotNull String methodName,
                                  @NotNull ObjectReference cancellationFileName,
                                  @NotNull ObjectReference progressFileName,
                                  @NotNull LongValue timeoutInMillis,
                                  @NotNull List<? extends Value> args) throws EvaluateException {

    DebuggerManagerThreadImpl.assertIsManagerThread();
    long start = System.currentTimeMillis();
    List<Method> methods = DebuggerUtilsEx.declaredMethodsByName(proxyType, methodName);
    if (methods.isEmpty()) {
      throw EvaluateExceptionUtil.createEvaluateException("Could not find method with such name: " + methodName);
    }
    if (methods.size() > 1) {
      throw EvaluateExceptionUtil.createEvaluateException("Too many methods \"" + methodName + "\" found. Count: " + methods.size());
    }

    Method method = methods.get(0);
    Value result = evaluationContext
      .computeAndKeep(() -> {
        DebugProcessImpl debugProcess = evaluationContext.getDebugProcess();
        ObjectReference proxyInstance = getProxyInstance(
          evaluationContext, proxyType, cancellationFileName, progressFileName, timeoutInMillis
        );
        return debugProcess.invokeInstanceMethod(
          evaluationContext, proxyInstance, method, args, ObjectReference.INVOKE_SINGLE_THREADED
        );
      });

    LOG.info("Memory agent's method \"" + methodName + "\" took " + (System.currentTimeMillis() - start) + " ms");
    return result;
  }

  private static byte @NotNull [] readUtilityClass() {
    return new ProxyExtractor().extractProxy();
  }

  @Nullable
  private static ReferenceType loadUtilityClass(@NotNull EvaluationContextImpl evaluationContext) throws EvaluateException {
    DebugProcessImpl debugProcess = evaluationContext.getDebugProcess();
    byte[] bytes = readUtilityClass();
    evaluationContext.setAutoLoadClasses(true);
    ClassLoaderReference classLoader = ClassLoadingUtils.getClassLoader(evaluationContext, debugProcess);
    ClassLoadingUtils.defineClass(PROXY_CLASS_NAME, bytes, evaluationContext, debugProcess, classLoader);

    try {
      ClassType systemClassType = (ClassType)debugProcess.findClass(evaluationContext, "java.lang.System", null);
      if (systemClassType == null) return null;

      String javaHomePath = getPropertyValue(evaluationContext, systemClassType, "java.home");
      if (javaHomePath == null) return null;

      JdkVersionDetector.JdkVersionInfo info = JdkVersionDetector.getInstance().detectJdkVersionInfo(javaHomePath);
      if (info == null) return null;

      String agentPath = getMemoryAgentPath(info.arch);
      if (agentPath == null) return null;

      setAgentPathPropertyValue(evaluationContext, systemClassType, agentPath);
      return debugProcess.loadClass(evaluationContext, PROXY_CLASS_NAME, classLoader);
    }
    catch (Exception e) {
      throw EvaluateExceptionUtil.createEvaluateException("Could not load proxy class", e);
    }
  }

  // Evaluates System.setProperty("intellij.memory.agent.path", agentPath)
  private static void setAgentPathPropertyValue(@NotNull EvaluationContextImpl evaluationContext,
                                                @NotNull ClassType systemClassType,
                                                @NotNull String agentPath) throws EvaluateException {
    DebugProcessImpl debugProcess = evaluationContext.getDebugProcess();
    Method setPropertyMethod = DebuggerUtils.findMethod(
      systemClassType, "setProperty", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"
    );
    if (setPropertyMethod == null) return;

    ObjectReference agentPathMirror = getStringReference(evaluationContext, agentPath);
    ObjectReference propertyNameMirror = getStringReference(evaluationContext, "intellij.memory.agent.path");
    debugProcess.invokeMethod(
      evaluationContext, systemClassType, setPropertyMethod, Arrays.asList(propertyNameMirror, agentPathMirror)
    );
  }

  private static @Nullable String getMemoryAgentPath(CpuArch arch) throws ExecutionException, InterruptedException, TimeoutException {
    return MemoryAgentUtil.getAgentFilePathAsString(Registry.is("debugger.memory.agent.debug"), MemoryAgentUtil.detectAgentKindByArch(arch));
  }

  // Evaluates System.getProperty(propertyName)
  @Nullable private static String getPropertyValue(@NotNull EvaluationContextImpl evaluationContext,
                                                   @NotNull ClassType systemClassType,
                                                   @NotNull String propertyName) throws EvaluateException {
    DebugProcessImpl debugProcess = evaluationContext.getDebugProcess();
    Method getPropertyMethod = DebuggerUtils.findMethod(
      systemClassType, "getProperty", "(Ljava/lang/String;)Ljava/lang/String;"
    );
    if (getPropertyMethod == null) return null;

    ObjectReference propertyNameMirror = getStringReference(evaluationContext, propertyName);
    StringReference propertyValueRef = (StringReference)debugProcess.invokeMethod(
      evaluationContext, systemClassType, getPropertyMethod, Collections.singletonList(propertyNameMirror)
    );

    return propertyValueRef == null ? null : propertyValueRef.value();
  }

  @NotNull
  private static ArrayReference wrapWithArray(@NotNull EvaluationContextImpl context, @NotNull List<ObjectReference> references)
    throws EvaluateException {
    long start = System.currentTimeMillis();
    ArrayType longArray = (ArrayType)context.getDebugProcess().findClass(context, "java.lang.Object[]", context.getClassLoader());
    ArrayReference instancesArray = longArray.newInstance(references.size());
    try {
      instancesArray.setValues(references);
    }
    catch (InvalidTypeException | ClassNotLoadedException e) {
      throw EvaluateExceptionUtil.createEvaluateException("Could not wrap objects with array", e);
    }
    LOG.info("Wrapping values with array took " + (System.currentTimeMillis() - start) + " ms");
    return instancesArray;
  }
}
