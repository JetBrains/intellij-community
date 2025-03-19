// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.rt.debugger.coroutines;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class CoroutinesDebugHelper {

  private static final String COROUTINE_OWNER_CLASS = "CoroutineOwner";
  private static final String DEBUG_METADATA_FQN = "kotlin.coroutines.jvm.internal.DebugMetadataKt";
  private static final String BASE_CONTINUATION_FQN = "kotlin.coroutines.jvm.internal.BaseContinuationImpl";
  private static final String DEBUG_COROUTINE_INFO_FQN = "kotlinx.coroutines.debug.internal.DebugCoroutineInfo";
  private static final String COROUTINE_CONTEXT_FQN = "kotlin.coroutines.CoroutineContext";
  private static final String COROUTINE_JOB_FQN = "kotlinx.coroutines.Job";
  private static final String COROUTINE_CONTEXT_KEY_FQN = "kotlin.coroutines.CoroutineContext$Key";

  public static long[] getCoroutinesRunningOnCurrentThread(Object debugProbes, Thread currentThread) throws ReflectiveOperationException {
    List<Long> coroutinesIds = new ArrayList<>();
    List infos = (List)invoke(debugProbes, "dumpCoroutinesInfo");
    for (Object info : infos) {
      if (invoke(info, "getLastObservedThread") == currentThread) {
        coroutinesIds.add((Long)invoke(info, "getSequenceNumber"));
      }
    }
    long[] res = new long[coroutinesIds.size()];
    for (int i = 0; i < res.length; i++) {
      res[i] = coroutinesIds.get(i).longValue();
    }
    return res;
  }

  public static long tryGetContinuationId(Object continuation) throws ReflectiveOperationException {
    Object rootContinuation = getCoroutineOwner(continuation, true);
    if (isCoroutineOwner(rootContinuation)) {
      Object debugCoroutineInfo = getField(rootContinuation, "info");
      return (long) getField(debugCoroutineInfo, "sequenceNumber");
    }
    return -1;
  }

  // This method tries to extract CoroutineOwner as a root coroutine frame,
  // it is invoked when kotlinx-coroutines debug agent is enabled.
  private static Object getCoroutineOwner(Object continuation, boolean checkForCoroutineOwner) throws ReflectiveOperationException {
    Method getCallerFrame = Class.forName("kotlin.coroutines.jvm.internal.CoroutineStackFrame", false, continuation.getClass().getClassLoader())
      .getDeclaredMethod("getCallerFrame");
    getCallerFrame.setAccessible(true);
    Object current = continuation;
    while (true) {
      if (checkForCoroutineOwner && isCoroutineOwner(current)) return current;
      Object parentFrame = getCallerFrame.invoke(current);
      if ((parentFrame != null)) {
        current = parentFrame;
      } else {
        return current;
      }
    }
  }

  public static Object getRootContinuation(Object continuation) throws ReflectiveOperationException {
    return getCoroutineOwner(continuation, false);
  }

  public static Object getCallerFrame(Object continuation) throws ReflectiveOperationException {
    // This method extracts the caller frame of the given continuation.
    Class<?> coroutineStackFrame = Class.forName("kotlin.coroutines.jvm.internal.CoroutineStackFrame", false, continuation.getClass().getClassLoader());
    Method getCallerFrame = coroutineStackFrame.getDeclaredMethod("getCallerFrame");
    getCallerFrame.setAccessible(true);
    Object callerFrame = getCallerFrame.invoke(continuation);
    // In case the caller frame is the root CoroutineOwner completion added by the debug agent -> return the current continuation
    if (callerFrame == null || isCoroutineOwner(callerFrame)) {
      return continuation;
    }
    // In case the caller frame is an instance of ScopeCoroutine, then extract the uCont that is wrapped by the ScopeCoroutine class.
    // ScopeCoroutine is used to wrap the current continuation and pass it into withContext/coroutineScope/flow.. invocation
    Class<?> scopeCoroutine = Class.forName("kotlinx.coroutines.internal.ScopeCoroutine", false, continuation.getClass().getClassLoader());
    if (scopeCoroutine.isInstance(callerFrame)) {
      return getCallerFrame.invoke(callerFrame);
    }
    return callerFrame;
  }

  /**
   * Returns continuation stack, and stack traces and variables required to restore coroutines stack.
   *
   * @return an array, where 0-th element is serialized String data to be restored with
   * {@link org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror.CoroutineStackTraceData};<p/>
   * 1-st element is an array of {@link kotlin.coroutines.Continuation} that form a stack of continuations
   */
  public static Object[] getCoroutineStackTraceDump(Object continuation) throws ReflectiveOperationException {
    List<Object> continuationStack = new ArrayList<>();
    List<StackTraceElement> continuationStackElements = new ArrayList<>();
    List<List<String>> variableNames = new ArrayList<>();
    List<List<String>> fieldNames = new ArrayList<>();

    ClassLoader loader = continuation.getClass().getClassLoader();
    Class<?> debugMetadata = Class.forName(DEBUG_METADATA_FQN, false, loader);
    Class<?> baseContinuation = Class.forName(BASE_CONTINUATION_FQN, false, loader);
    Method getStackTraceElement = debugMetadata.getDeclaredMethod("getStackTraceElement", baseContinuation);
    getStackTraceElement.setAccessible(true);
    Method getSpilledVariableFieldMapping = debugMetadata.getDeclaredMethod("getSpilledVariableFieldMapping", baseContinuation);
    getSpilledVariableFieldMapping.setAccessible(true);

    Object current = continuation;
    do {
      StackTraceElement stackTraceElement = (StackTraceElement)getStackTraceElement.invoke(null, current);
      continuationStackElements.add(stackTraceElement);

      List<String> fields = new ArrayList<>();
      List<String> names = new ArrayList<>();
      extractSpilledVariables(current, names, fields, getSpilledVariableFieldMapping);
      variableNames.add(names);
      fieldNames.add(fields);
      continuationStack.add(current);

      current = invoke(current, "getCompletion");
    }
    while (current != null && baseContinuation.isInstance(current));

    List<StackTraceElement> creationStack = null;
    if (current != null && isCoroutineOwner(current)) {
      Object debugCoroutineInfo = getField(current, "info");
      //noinspection unchecked
      creationStack = (List<StackTraceElement>)invoke(debugCoroutineInfo, "getCreationStackTrace");
    }

    String json = JsonUtils.dumpCoroutineStackTraceDumpToJson(continuationStackElements, variableNames, fieldNames, creationStack);
    return new Object[]{json, continuationStack.toArray(),};
  }

  private static void extractSpilledVariables(Object continuation,
                                              List<String> variableNames, List<String> fieldNames,
                                              Method getSpilledVariableFieldMapping) throws ReflectiveOperationException {
    String[] mapping = (String[])getSpilledVariableFieldMapping.invoke(null, continuation);
    if (mapping == null) return;
    for (int i = 0; i < mapping.length; i += 2) {
      fieldNames.add(mapping[i]);
      variableNames.add(mapping[i + 1]);
    }
  }

  private static boolean isCoroutineOwner(Object current) {
    return current.getClass().getSimpleName().contains(COROUTINE_OWNER_CLASS);
  }

  public static Object[] dumpCoroutinesInfoAsJsonAndReferences() throws ReflectiveOperationException {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    try {
      Class<?> debugProbesImplClass = classLoader.loadClass("kotlinx.coroutines.debug.internal.DebugProbesImpl");
      Object debugProbesImplInstance = debugProbesImplClass.getField("INSTANCE").get(null);
      Object[] infos = (Object[])invoke(debugProbesImplInstance, "dumpCoroutinesInfoAsJsonAndReferences");
      return infos;
    } catch (Throwable e) {
      return null;
    }
  }

  /**
   * This method takes the array of {@link kotlinx.coroutines.debug.internal.DebugCoroutineInfo} instances
   * and for each coroutine requests it's job, and it's first parent.
   *
   * @return an array of Strings of size (debugCoroutineInfos.size * 2), where
   * (2 * i)-th element is a String representation of the job and
   * (2 * i + 1)-th element is a String representation of the parent of the i-th coroutine from debugCoroutineInfos array.
   */
  public static String[] getJobsAndParentsForCoroutines(Object ... debugCoroutineInfos) throws ReflectiveOperationException {
    if (debugCoroutineInfos.length == 0) return new String[]{};
    String[] jobsWithParents = new String[debugCoroutineInfos.length * 2];
    ClassLoader loader = debugCoroutineInfos[0].getClass().getClassLoader();
    Class<?> debugCoroutineInfoClass = Class.forName(DEBUG_COROUTINE_INFO_FQN, false, loader);
    Class<?> coroutineContext = Class.forName(COROUTINE_CONTEXT_FQN, false, loader);
    Class<?> coroutineContextKey = Class.forName(COROUTINE_CONTEXT_KEY_FQN, false, loader);
    Class<?> coroutineJobClass = Class.forName(COROUTINE_JOB_FQN, false, loader);
    Object coroutineJobKey = coroutineJobClass.getField("Key").get(null); // Job.Key
    Method coroutineContextGet = coroutineContext.getMethod("get", coroutineContextKey);
    Method getParentJob = coroutineJobClass.getMethod("getParent");
    Method getContext = debugCoroutineInfoClass.getMethod("getContext");

    for (int i = 0; i < debugCoroutineInfos.length * 2; i += 2) {
      Object info = debugCoroutineInfos[i / 2];
      if (info == null) {
        jobsWithParents[i] = null;
        jobsWithParents[i + 1] = null;
        continue;
      }
      Object context = invoke(info, getContext);
      Object job = invoke(context, coroutineContextGet, coroutineJobKey);
      Object parent = invoke(job, getParentJob);
      jobsWithParents[i] = (job == null) ? null : job.toString();
      jobsWithParents[i + 1] = (parent == null) ? null : parent.toString();
    }
    return jobsWithParents;
  }

  private static Object getField(Object object, String fieldName) throws ReflectiveOperationException {
    Field field = object.getClass().getField(fieldName);
    field.setAccessible(true);
    return field.get(object);
  }

  private static Object invoke(Object object, Method method, Object... args) throws ReflectiveOperationException {
    method.setAccessible(true);
    return method.invoke(object, args);
  }

  private static Object invoke(Object object, String methodName) throws ReflectiveOperationException {
    Method method = object.getClass().getMethod(methodName);
    method.setAccessible(true);
    return method.invoke(object);
  }
}