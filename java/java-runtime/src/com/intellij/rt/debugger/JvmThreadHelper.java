// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.rt.debugger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Stream;


@SuppressWarnings({"unchecked", "unused"})
public final class JvmThreadHelper {

  public static final int METHOD_STACK_STATUS_FOUND = 1;
  public static final int METHOD_STACK_STATUS_NOT_FOUND = 2;
  public static final int METHOD_STACK_STATUS_INCOMPLETE_STACKS = 3;
  public static final int METHOD_STACK_STATUS_TOO_MANY_INCOMPLETE_STACKS = 4;
  public static final int METHOD_STACK_STATUS_TIMED_OUT = 5;
  public static final int METHOD_STACK_SCAN_UNLIMITED_DEPTH = -1;

  private static final int MAX_INCOMPLETE_STACKS = 32;

  private final MethodHandle containersRootHandle;
  private final MethodHandle containerChildrenHandle;
  private final MethodHandle containerThreadsHandle;
  private final MethodHandle containerNameHandle;
  private final MethodHandle containerOwnerHandle;
  private final MethodHandle carrierThreadHandle;

  private final MethodHandle threadIsVirtualHandle;
  private final MethodHandle threadThreadState;

  private int threadsCount = 0;
  private final HashMap<String, ArrayList<Thread>> threadsGroupedByStackTrace = new HashMap<>();

  private final ArrayList<String> containerNames = new ArrayList<>();
  private final ArrayList<Object> containerOwners = new ArrayList<>();
  private final ArrayList<Object> containerReferences = new ArrayList<>();
  private final ArrayList<Integer> containerParentOrdinals = new ArrayList<>();

  private JvmThreadHelper(MethodHandles.Lookup lookup) throws Throwable {
    // ThreadContainer & Co., since Java 21
    Class<?> threadContainersClass = Class.forName("jdk.internal.vm.ThreadContainers", false, ClassLoader.getSystemClassLoader());
    Class<?> threadContainerClass = Class.forName("jdk.internal.vm.ThreadContainer", false, ClassLoader.getSystemClassLoader());
    Class<?> virtualThreadClass = Class.forName("java.lang.VirtualThread", false, ClassLoader.getSystemClassLoader());
    containersRootHandle = lookup.findStatic(threadContainersClass, "root", MethodType.methodType(threadContainerClass));
    containerChildrenHandle = lookup.findVirtual(threadContainerClass, "children", MethodType.methodType(Stream.class));
    containerThreadsHandle = lookup.findVirtual(threadContainerClass, "threads", MethodType.methodType(Stream.class));
    containerNameHandle = lookup.findVirtual(threadContainerClass, "name", MethodType.methodType(String.class));
    containerOwnerHandle = lookup.findVirtual(threadContainerClass, "owner", MethodType.methodType(Thread.class));

    // VirtualThread & Co., since Java 21
    threadIsVirtualHandle = lookup.findVirtual(Thread.class, "isVirtual", MethodType.methodType(boolean.class));
    // private field java.lang.VirtualThread#carrierThread
    carrierThreadHandle = lookup.findGetter(virtualThreadClass, "carrierThread", Thread.class);
    // Thread, non-public method
    threadThreadState = lookup.findVirtual(Thread.class, "threadState", MethodType.methodType(Thread.State.class));
  }

  /**
   * Returns an Object array containing information about virtual threads with the following elements:
   * <ol>
   *   <li> {@code Object[]} - all virtual threads grouped by equal stack traces.
   *     The elements in this array are packed as follows:
   *     <ul>
   *       <li>{@link String} representing the common stack trace for the group.
   *         It includes the thread name, thread state, thread container ordinal (in the following array), and the stack frames.</li>
   *       <li>Then, there are one or many thread objects as {@link com.sun.jdi.ThreadReference ThreadReferences}
   *         and each of them has the above stack trace.</li>
   *       <li>After the last thread object, there is a single {@code null} as a delimiter.</li>
   *       <li>Then we have a new group of stack trace and threads, or the array ends.</li>
   *     </ul>
   *   </li>
   *   <li> {@code long[]} - thread IDs of threads from the first array in the corresponding order.</li>
   *   <li> {@code long[]} - thread IDs of carriers of virtual threads (see {@code java.lang.VirtualThread#carrierThread}) if they are mounted
   *     or -1 if a virtual thread is not mounted, in the same order as their IDs in the array above. </li>
   *   <li> {@code String[]} - names of all {@code jdk.internal.vm.ThreadContainer}s, they are referenced from the first array by ordinals.</li>
   *   <li> {@code Object[]} - {@code jdk.internal.vm.ThreadContainer} objects in the same order as their names in the array above.</li>
   *   <li> {@code Object[]} - scope owners (see {@code jdk.internal.vm.StackableScope#owner}) of thread containers
   *     as {@link com.sun.jdi.ThreadReference ThreadReferences}, in the same order as thread containers in the array above.</li>
   *   <li> {@code int[]} - ordinals of the parent container for every thread container or -1 if there is no parent.</li>
   * </ol>
   */
  public static Object[] getAllVirtualThreadsWithStackTracesAndContainers(MethodHandles.Lookup lookup) throws Throwable {
    JvmThreadHelper dumper = new JvmThreadHelper(lookup);
    return dumper.collect();
  }

  /**
   * @param methods flat array of class/method pairs (signatures): {@code [className1, methodName1, className2, methodName2, ...]}.
   * @param maxStackTraceFramesToScan maximum number of top stack frames to check, or non-positive for unlimited scanning.
   * @param threadToSkip thread that was already checked by the debugger, or {@code null}.
   * @param timeoutMillis scan timeout in milliseconds, or non-positive for unlimited scanning.
   * @return an {@code Object[]} whose first element is one of the {@code METHOD_STACK_STATUS_*} values. For
   * {@link #METHOD_STACK_STATUS_INCOMPLETE_STACKS}, the remaining elements are {@link Thread}s whose stacks should be checked explicitly.
   * For all other statuses, the array contains only the status element.
   */
  public static Object[] findSpecifiedMethodsInAnyThreadStack(
    MethodHandles.Lookup lookup,
    String[] methods,
    int maxStackTraceFramesToScan,
    Thread threadToSkip,
    int timeoutMillis
  ) throws Throwable {
    MethodTarget[] targets = MethodTarget.from(methods);
    if (targets.length == 0) return new Object[] {METHOD_STACK_STATUS_NOT_FOUND};

    MethodStackScanResult result = new MethodStackScanResult(maxStackTraceFramesToScan, threadToSkip, timeoutMillis);
    for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
      int status = result.scan(entry.getKey(), entry.getValue(), targets);
      if (status != METHOD_STACK_STATUS_NOT_FOUND) return result.toObjectArray(status);
    }

    if (lookup != null) {
      int status = new JvmThreadHelper(lookup).findMethodInVirtualThreadStack(targets, result);
      if (status != METHOD_STACK_STATUS_NOT_FOUND) return result.toObjectArray(status);
    }

    return result.toObjectArray(
      result.hasIncompleteStacks() ? METHOD_STACK_STATUS_INCOMPLETE_STACKS : METHOD_STACK_STATUS_NOT_FOUND
    );
  }

  private Object[] collect() throws Throwable {
    // Collect all threads and containers starting from the root container.
    processContainer(containersRootHandle.invoke(), -1);

    // Group threads by stack trace and compact them into arrays.
    long[] threadIds = new long[threadsCount];
    long[] carrierThreadIds = new long[threadsCount];
    int tidIdx = 0;
    Object[] allStackTraceAndThreads = new Object[threadsCount + threadsGroupedByStackTrace.size() * 2];
    int stIdx = 0;
    for (Map.Entry<String, ArrayList<Thread>> e : threadsGroupedByStackTrace.entrySet()) {
      String st = e.getKey();
      ArrayList<Thread> ts = e.getValue();
      allStackTraceAndThreads[stIdx++] = st;
      for (Thread t : ts) {
        allStackTraceAndThreads[stIdx++] = t;
        threadIds[tidIdx] = t.getId();
        carrierThreadIds[tidIdx] = getCarrierThreadId(t);
        tidIdx++;
      }
      allStackTraceAndThreads[stIdx++] = null;
    }
    assert tidIdx == threadsCount;
    assert stIdx == allStackTraceAndThreads.length;

    return new Object[] {
      allStackTraceAndThreads,
      threadIds,
      carrierThreadIds,
      containerNames.toArray(),
      containerReferences.toArray(),
      containerOwners.toArray(),
      containerParentOrdinals.stream().mapToInt(Integer::intValue).toArray()
    };
  }

  private void processContainer(Object container, int parentContainerOrdinal) throws Throwable {
    int containerOrdinal = saveContainerInfo(container, parentContainerOrdinal);
    saveVirtualThreadsInfo(container, containerOrdinal);
    processContainerChildren(container, containerOrdinal);
  }

  private int findMethodInVirtualThreadStack(MethodTarget[] targets, MethodStackScanResult result) throws Throwable {
    return findMethodInVirtualThreadStack(containersRootHandle.invoke(), targets, result);
  }

  private int findMethodInVirtualThreadStack(Object container, MethodTarget[] targets, MethodStackScanResult result) throws Throwable {
    Iterator<Thread> threads = ((Stream<Thread>)containerThreadsHandle.invoke(container)).iterator();
    while (threads.hasNext()) {
      Thread t = threads.next();
      boolean isVirtual = (boolean)threadIsVirtualHandle.invoke(t);
      if (isVirtual) {
        int status = result.scan(t, t.getStackTrace(), targets);
        if (status != METHOD_STACK_STATUS_NOT_FOUND) return status;
      }
    }

    Iterator<Object> children = ((Stream<Object>)containerChildrenHandle.invoke(container)).iterator();
    while (children.hasNext()) {
      int status = findMethodInVirtualThreadStack(children.next(), targets, result);
      if (status != METHOD_STACK_STATUS_NOT_FOUND) return status;
    }
    return METHOD_STACK_STATUS_NOT_FOUND;
  }

  private void saveVirtualThreadsInfo(Object container, int containerOrdinal) throws Throwable {
    Iterator<Thread> threads = ((Stream<Thread>)containerThreadsHandle.invoke(container)).iterator();
    while (threads.hasNext()) {
      Thread t = threads.next();

      boolean isVirtual = (boolean)threadIsVirtualHandle.invoke(t);
      if (!isVirtual) continue;

      String name = t.getName();
      Thread.State javaThreadState = (Thread.State)threadThreadState.invoke(t);

      // "Stack trace" format, in such a way it should be shared between multiple threads and easily processed on the debugger side:
      // <name>
      // <javaThreadState>
      // <threadContainerOrdinal>
      // <stack trace elements...>
      StringBuilder buffer = new StringBuilder();
      buffer.append(name).append('\n')
        .append(javaThreadState).append('\n')
        .append(containerOrdinal);
      for (StackTraceElement ste : t.getStackTrace()) {
        buffer.append("\n\tat ").append(ste);
      }
      String stackTrace = buffer.toString();

      ArrayList<Thread> similarThreads = threadsGroupedByStackTrace.get(stackTrace);
      if (similarThreads == null) {
        similarThreads = new ArrayList<>();
        threadsGroupedByStackTrace.put(stackTrace, similarThreads);
      }
      similarThreads.add(t);
      threadsCount++;
    }
  }

  private int saveContainerInfo(Object container, int parentContainerOrdinal) throws Throwable {
    assert containerNames.size() == containerParentOrdinals.size();
    int ordinal = containerNames.size();
    containerNames.add(getContainerName(container));
    containerOwners.add(containerOwnerHandle.invoke(container));
    containerReferences.add(container);
    containerParentOrdinals.add(parentContainerOrdinal);
    return ordinal;
  }

  private void processContainerChildren(Object container, int ordinal) throws Throwable {
    Iterator<Object> children = ((Stream<Object>)containerChildrenHandle.invoke(container)).iterator();
    while (children.hasNext()) {
      Object childContainer = children.next();
      processContainer(childContainer, ordinal);
    }
  }

  private String getContainerName(Object container) throws Throwable {
    String name = (String)containerNameHandle.invoke(container);
    return name != null ? name : container.toString();
  }

  private long getCarrierThreadId(Thread t) throws Throwable {
    Thread carrierThread = (Thread)carrierThreadHandle.invoke(t);
    return carrierThread != null ? carrierThread.getId() : -1;
  }

  private static boolean hasMethodInStackTrace(StackTraceElement[] stackTrace, int framesToScan, MethodTarget[] targets) {
    for (int i = 0; i < framesToScan; i++) {
      StackTraceElement element = stackTrace[i];
      for (MethodTarget target : targets) {
        if (target.matches(element)) return true;
      }
    }
    return false;
  }

  private static boolean hasKnownRootFrame(Thread thread, StackTraceElement[] stackTrace) {
    if (stackTrace.length == 0) return true; // It should be rare case and let's consider it as known root
    StackTraceElement root = stackTrace[stackTrace.length - 1];
    if ("main".equals(root.getMethodName())) return true;
    if (!"run".equals(root.getMethodName())) return false;

    String className = root.getClassName();
    if ("java.lang.Thread".equals(className) ||
        "java.lang.VirtualThread".equals(className) ||
        "java.util.concurrent.ForkJoinWorkerThread".equals(className)) {
      return true;
    }

    for (Class<?> current = thread.getClass(); current != null; current = current.getSuperclass()) {
      if (className.equals(current.getName())) return true;
    }
    return false;
  }

  private static final class MethodStackScanResult {
    private final Thread threadToSkip;
    private final boolean hasDeadline;
    private final long deadlineNanos;
    private final int maxStackTraceFramesToScan;
    private final ArrayList<Thread> incompleteThreads = new ArrayList<>();

    private MethodStackScanResult(int maxStackTraceFramesToScan, Thread threadToSkip, int timeoutMillis) {
      this.threadToSkip = threadToSkip;
      hasDeadline = timeoutMillis > 0;
      deadlineNanos = hasDeadline ? System.nanoTime() + (long)timeoutMillis * 1_000_000L : 0;
      this.maxStackTraceFramesToScan = maxStackTraceFramesToScan;
    }

    private int scan(Thread thread, StackTraceElement[] stackTrace, MethodTarget[] targets) {
      if (thread == threadToSkip) return METHOD_STACK_STATUS_NOT_FOUND;
      if (isTimedOut()) return METHOD_STACK_STATUS_TIMED_OUT;
      int framesToScan = maxStackTraceFramesToScan <= 0
                         ? stackTrace.length
                         : Math.min(stackTrace.length, maxStackTraceFramesToScan);
      if (hasMethodInStackTrace(stackTrace, framesToScan, targets)) return METHOD_STACK_STATUS_FOUND;
      if (stackTrace.length > framesToScan || !hasKnownRootFrame(thread, stackTrace)) {
        if (incompleteThreads.size() >= MAX_INCOMPLETE_STACKS) return METHOD_STACK_STATUS_TOO_MANY_INCOMPLETE_STACKS;
        incompleteThreads.add(thread);
      }
      return METHOD_STACK_STATUS_NOT_FOUND;
    }

    private boolean hasIncompleteStacks() {
      return !incompleteThreads.isEmpty();
    }

    private boolean isTimedOut() {
      return hasDeadline && System.nanoTime() - deadlineNanos >= 0;
    }

    private Object[] toObjectArray(int status) {
      if (status != METHOD_STACK_STATUS_INCOMPLETE_STACKS) return new Object[] {status};

      Object[] result = new Object[incompleteThreads.size() + 1];
      result[0] = status;
      for (int i = 0; i < incompleteThreads.size(); i++) {
        result[i + 1] = incompleteThreads.get(i);
      }
      return result;
    }
  }

  private static final class MethodTarget {
    private final String className;
    private final String methodName;

    private MethodTarget(String className, String methodName) {
      this.className = className;
      this.methodName = methodName;
    }

    private boolean matches(StackTraceElement element) {
      return className.equals(element.getClassName()) && methodName.equals(element.getMethodName());
    }

    private static MethodTarget[] from(String[] methods) {
      if (methods.length % 2 != 0) {
        throw new IllegalArgumentException("Expected class/method pairs");
      }
      MethodTarget[] result = new MethodTarget[methods.length / 2];
      for (int i = 0; i < methods.length; i += 2) {
        result[i / 2] = new MethodTarget(methods[i], methods[i + 1]);
      }
      return result;
    }
  }
}
