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


@SuppressWarnings("unchecked")
public final class VirtualThreadDumper {

  private final MethodHandle containersRootHandle;
  private final MethodHandle containerChildrenHandle;
  private final MethodHandle containerThreadsHandle;

  private final MethodHandle threadIsVirtualHandle;
  private final MethodHandle threadThreadState;

  private int threadsCount = 0;
  private final HashMap<String, ArrayList<Thread>> threadsGroupedByStackTrace = new HashMap<>();

  private final ArrayList<String> containerNames = new ArrayList<>();
  private final ArrayList<Object> containerReferences = new ArrayList<>();
  private final ArrayList<Integer> containerParentOrdinals = new ArrayList<>();

  private VirtualThreadDumper(MethodHandles.Lookup lookup) throws Throwable {
    // ThreadContainer & Co., since Java 21
    Class<?> threadContainersClass = Class.forName("jdk.internal.vm.ThreadContainers");
    Class<?> threadContainerClass = Class.forName("jdk.internal.vm.ThreadContainer");
    containersRootHandle = lookup.findStatic(threadContainersClass, "root", MethodType.methodType(threadContainerClass));
    containerChildrenHandle = lookup.findVirtual(threadContainerClass, "children", MethodType.methodType(Stream.class));
    containerThreadsHandle = lookup.findVirtual(threadContainerClass, "threads", MethodType.methodType(Stream.class));

    // VirtualThread & Co., since Java 21
    threadIsVirtualHandle = lookup.findVirtual(Thread.class, "isVirtual", MethodType.methodType(boolean.class));
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
   *   <li> {@code String[]} - names of all {@code jdk.internal.vm.ThreadContainer}s, they are referenced from the first array by ordinals.</li>
   *   <li> {@code Object[]} - {@code jdk.internal.vm.ThreadContainer} objects in the same order as their names in the array above.</li>
   *   <li> {@code int[]} - ordinals of the parent container for every thread container or -1 if there is no parent.</li>
   * </ol>
   */
  public static Object[] getAllVirtualThreadsWithStackTracesAndContainers(MethodHandles.Lookup lookup) throws Throwable {
    VirtualThreadDumper dumper = new VirtualThreadDumper(lookup);
    return dumper.collect();
  }

  private Object[] collect() throws Throwable {
    // Collect all threads and containers starting from the root container.
    processContainer(containersRootHandle.invoke(), -1);

    // Group threads by stack trace and compact them into arrays.
    long[] threadIds = new long[threadsCount];
    int tidIdx = 0;
    Object[] allStackTraceAndThreads = new Object[threadsCount + threadsGroupedByStackTrace.size() * 2];
    int stIdx = 0;
    for (Map.Entry<String, ArrayList<Thread>> e : threadsGroupedByStackTrace.entrySet()) {
      String st = e.getKey();
      ArrayList<Thread> ts = e.getValue();
      allStackTraceAndThreads[stIdx++] = st;
      for (Thread t : ts) {
        allStackTraceAndThreads[stIdx++] = t;
        threadIds[tidIdx++] = t.getId();
      }
      allStackTraceAndThreads[stIdx++] = null;
    }
    assert tidIdx == threadsCount;
    assert stIdx == allStackTraceAndThreads.length;

    return new Object[] {
      allStackTraceAndThreads,
      threadIds,
      containerNames.toArray(),
      containerReferences.toArray(),
      containerParentOrdinals.stream().mapToInt(Integer::intValue).toArray()
    };
  }

  private void processContainer(Object container, int parentContainerOrdinal) throws Throwable {
    int containerOrdinal = saveContainerInfo(container, parentContainerOrdinal);
    saveVirtualThreadsInfo(container, containerOrdinal);
    processContainerChildren(container, containerOrdinal);
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
    containerNames.add(container.toString());
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
}
