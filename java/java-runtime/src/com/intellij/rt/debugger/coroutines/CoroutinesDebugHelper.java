// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.rt.debugger.coroutines;

import com.intellij.rt.debugger.JsonUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CoroutinesDebugHelper {

  private static final String COROUTINE_OWNER_CLASS = "CoroutineOwner";
  private static final String DEBUG_METADATA_FQN = "kotlin.coroutines.jvm.internal.DebugMetadataKt";
  private static final String BASE_CONTINUATION_FQN = "kotlin.coroutines.jvm.internal.BaseContinuationImpl";
  private static final String COROUTINE_STACK_FRAME_FQN = "kotlin.coroutines.jvm.internal.CoroutineStackFrame";
  private static final String DEBUG_COROUTINE_INFO_FQN = "kotlinx.coroutines.debug.internal.DebugCoroutineInfo";
  private static final String COROUTINE_CONTEXT_FQN = "kotlin.coroutines.CoroutineContext";
  private static final String COROUTINE_JOB_FQN = "kotlinx.coroutines.Job";
  private static final String COROUTINE_CONTEXT_KEY_FQN = "kotlin.coroutines.CoroutineContext$Key";
  private static final String DEBUGGER_AGENT_CAPTURE_STORAGE_FQN = "com.intellij.rt.debugger.agent.CaptureStorage";

  public static long[] getCoroutinesRunningOnCurrentThread(Class<?> debugProbesImplClass, Thread currentThread) throws ReflectiveOperationException {
    Object debugProbesImplInstance = debugProbesImplClass.getField("INSTANCE").get(null);
    List<Long> coroutinesIds = new ArrayList<>();
    List infos = (List)invoke(debugProbesImplInstance, "dumpCoroutinesInfo");
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
    Class<?> coroutineStackFrame = Class.forName(COROUTINE_STACK_FRAME_FQN, false, loader);
    Method getStackTraceElement = coroutineStackFrame.getDeclaredMethod("getStackTraceElement");
    Method callerFrame = coroutineStackFrame.getDeclaredMethod("getCallerFrame");
    Method getSpilledVariableFieldMapping = debugMetadata.getDeclaredMethod("getSpilledVariableFieldMapping", baseContinuation);
    getSpilledVariableFieldMapping.setAccessible(true);

    Object current = continuation;
    while(current != null && coroutineStackFrame.isInstance(current) && !isCoroutineOwner(current)) {

      StackTraceElement stackTraceElement = (StackTraceElement)invoke(current, getStackTraceElement);
      continuationStackElements.add(stackTraceElement);

      if (baseContinuation.isInstance(current)) {
        List<String> fields = new ArrayList<>();
        List<String> names = new ArrayList<>();

        extractSpilledVariables(current, names, fields, getSpilledVariableFieldMapping);

        variableNames.add(names);
        fieldNames.add(fields);
      } else {
        variableNames.add(Collections.emptyList());
        fieldNames.add(Collections.emptyList());
      }

      continuationStack.add(current);
      current = invoke(current, callerFrame);
    }

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

  public static Object[] dumpCoroutinesInfoAsJsonAndReferences(Class<?> debugProbesImplClass) {
    try {
      Object debugProbesImplInstance = debugProbesImplClass.getField("INSTANCE").get(null);
      Object[] infos = (Object[])invoke(debugProbesImplInstance, "dumpCoroutinesInfoAsJsonAndReferences");
      return infos;
    } catch (Throwable e) {
      return null;
    }
  }

  public static Object[] dumpCoroutinesWithStacktracesAsJson(Class<?> debugProbesImplClass) {
    try {
      Object debugProbesImplInstance = debugProbesImplClass.getField("INSTANCE").get(null);
      Object[] dump = (Object[])invoke(debugProbesImplInstance, "dumpCoroutinesInfoAsJsonAndReferences");
      Object[] coroutineInfos = (Object[])dump[3];
      String[] lastObservedStackTraces = new String[coroutineInfos.length];
      for (int i = 0; i < coroutineInfos.length; i++) {
        lastObservedStackTraces[i] = lastObservedStackTrace(coroutineInfos[i]);
      }
      dump = Arrays.copyOf(dump, dump.length + 2);
      dump[4] = lastObservedStackTraces;

      Object[] lastObservedThreads = (Object[])dump[1];
      dump[5] = getAsyncStackTracesForThreads(lastObservedThreads);
      return dump;
    } catch (Throwable e) {
      return null;
    }
  }

  private static String lastObservedStackTrace(Object debugCoroutineInfo) throws ReflectiveOperationException {
    List<StackTraceElement> stackTrace = (List<StackTraceElement>)invoke(debugCoroutineInfo, "lastObservedStackTrace");
    return JsonUtils.dumpStackTraceElements(stackTrace);
  }

  /**
   * Invokes com.intellij.rt.debugger.agent.CaptureStorage#getAllCapturedStacks method
   * which returns a map of threads to their captured async stack traces.
   * If `debugger.async.stack.trace.for.all.threads` is false, only the current thread's stack trace is returned.
   *
   * If debugger-agent is not available, e.g. in attach, returns null
   */
  private static String[] getAsyncStackTracesForThreads(Object[] threads) {
    try {
      Class<?> captureStorageClass = Class.forName(DEBUGGER_AGENT_CAPTURE_STORAGE_FQN, false, null);
      Method getAllCapturedStacks = captureStorageClass.getMethod("getAllCapturedStacks", int.class);

      Map<Thread, String> threadToStackTrace = (Map<Thread, String>)invoke(null, getAllCapturedStacks, 500);

      String[] asyncStackTraces = new String[threads.length];

      for (int i = 0; i < threads.length; i++) {
        Object thread = threads[i];
        if (thread != null) {
          asyncStackTraces[i] = threadToStackTrace.get(thread);
        }
      }
      return asyncStackTraces;
    } catch (Throwable e) {
      return null;
    }
  }

  /**
   * Returns an Object array containing coroutine job hierarchy information, or {@code null} if something went wrong
   * or job corresponding to some coroutine does not exist.
   * <ol>
   *   <li> {@code String[]} - string representations of the {@code kotlinx.coroutines.Job Job} extracted
   *     from each coroutine's context, in the same order as the input array.</li>
   *   <li> {@code Object[]} - the actual {@code kotlinx.coroutines.Job Job} objects corresponding to each coroutine.</li>
   *   <li> {@code int[]} - for each job, the index of its nearest ancestor job that is also present
   *     in this dump, or -1 if no such parent exists.</li>
   * </ol>
   */
  public static Object[] getCoroutineJobHierarchyInfo(Object ... debugCoroutineInfos) throws ReflectiveOperationException {
    if (debugCoroutineInfos.length == 0) return new String[]{};
    ClassLoader loader = debugCoroutineInfos[0].getClass().getClassLoader();
    Class<?> debugCoroutineInfoClass = Class.forName(DEBUG_COROUTINE_INFO_FQN, false, loader);
    Class<?> coroutineContext = Class.forName(COROUTINE_CONTEXT_FQN, false, loader);
    Class<?> coroutineContextKey = Class.forName(COROUTINE_CONTEXT_KEY_FQN, false, loader);
    Class<?> coroutineJobClass = Class.forName(COROUTINE_JOB_FQN, false, loader);
    Object coroutineJobKey = coroutineJobClass.getField("Key").get(null); // Job.Key
    Method coroutineContextGet = coroutineContext.getMethod("get", coroutineContextKey);
    Method getParentJob = coroutineJobClass.getMethod("getParent");
    Method getContext = debugCoroutineInfoClass.getMethod("getContext");

    String[] jobNames = new String[debugCoroutineInfos.length];
    Object[] jobObjects = new Object[debugCoroutineInfos.length];
    int[] parentIndexes = new int[debugCoroutineInfos.length];
    Arrays.fill(parentIndexes, -1);

    for (int i = 0; i < debugCoroutineInfos.length; i++) {
      Object info = debugCoroutineInfos[i];
      Object context = invoke(info, getContext);
      Object job = invoke(context, coroutineContextGet, coroutineJobKey);
      // job corresponding to the coroutine should not be null, coroutines should be dumped again, fast return
      if (job == null) return null;
      jobNames[i] = job.toString();
      jobObjects[i] = job;
    }
    // we use this set of coroutine jobs to only save parent jobs which correspond to some coroutine in the dump
    Map<Object, Integer> jobToIndex = new HashMap<>();
    for (int i = 0; i < jobObjects.length; i++) {
      Object obj = jobObjects[i];
      jobToIndex.put(obj, i);
    }

    for (int i = 0; i < debugCoroutineInfos.length; i++) {
      Object job = jobObjects[i];
      Object parent = invoke(job, getParentJob);
      while (parent != null) {
        if (jobToIndex.containsKey(parent)) {
          parentIndexes[i] = jobToIndex.get(parent);
          break;
        }
        parent = invoke(parent, getParentJob);
      }
    }
    return new Object[]{jobNames, jobObjects, parentIndexes};
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