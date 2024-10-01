// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author Eugene Zhuravlev
 */
package com.intellij.debugger.jdi;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.jdi.VirtualMachineProxy;
import com.intellij.debugger.impl.DebuggerUtilsAsync;
import com.intellij.debugger.impl.attach.SAJDWPRemoteConnection;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jdi.ReferenceTypeImpl;
import com.jetbrains.jdi.ThreadReferenceImpl;
import com.sun.jdi.*;
import com.sun.jdi.request.EventRequestManager;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;

public class VirtualMachineProxyImpl implements JdiTimer, VirtualMachineProxy {
  private static final Logger LOG = Logger.getInstance(VirtualMachineProxyImpl.class);
  private final DebugProcessImpl myDebugProcess;
  private final VirtualMachine myVirtualMachine;
  private int myTimeStamp = 0;
  private int myModelSuspendCount = 0;

  private final Map<String, StringReference> myStringLiteralCache = new HashMap<>();

  @NotNull
  private final Map<ThreadReference, ThreadReferenceProxyImpl> myAllThreads = new ConcurrentHashMap<>();
  private final Map<ThreadGroupReference, ThreadGroupReferenceProxyImpl> myThreadGroups = new HashMap<>();
  private boolean myAllThreadsDirty = true;
  private List<ReferenceType> myAllClasses;
  private Map<ReferenceType, List<ReferenceType>> myNestedClassesCache = new HashMap<>();

  private final boolean myVersionHigher_15;
  private final boolean myVersionHigher_14;

  public VirtualMachineProxyImpl(DebugProcessImpl debugProcess, @NotNull VirtualMachine virtualMachine) {
    myVirtualMachine = virtualMachine;
    myDebugProcess = debugProcess;

    // All versions of Dalvik/ART support at least the JDWP spec as of 1.6.
    myVersionHigher_15 = DebuggerUtils.isAndroidVM(myVirtualMachine) || versionHigher("1.5");
    myVersionHigher_14 = myVersionHigher_15 || versionHigher("1.4");

    // avoid lazy-init for some properties: the following will pre-calculate values
    canRedefineClasses(); // fetch capabilitiesNew
    canWatchFieldModification(); // fetch capabilities

    if (canBeModified()) { // no need to spend time here for read only sessions
      // this will cache classes inside JDI and enable faster search of classes later
      DebuggerUtilsAsync.allCLasses(virtualMachine);
    }

    virtualMachine.topLevelThreadGroups().forEach(this::threadGroupCreated);
  }

  @NotNull
  public VirtualMachine getVirtualMachine() {
    return myVirtualMachine;
  }

  public ClassesByNameProvider getClassesByNameProvider() {
    return this::classesByName;
  }

  @Override
  public List<ReferenceType> classesByName(@NotNull String s) {
    return myVirtualMachine.classesByName(s);
  }

  @Override
  public List<ReferenceType> nestedTypes(ReferenceType refType) {
    List<ReferenceType> nestedTypes = myNestedClassesCache.get(refType);
    if (nestedTypes == null) {
      List<ReferenceType> list = Collections.emptyList();
      try {
        list = refType.nestedTypes();
      }
      catch (Throwable e) {
        // sometimes some strange errors are thrown from JDI. Do not crash debugger because of this.
        // Example:
        //java.lang.StringIndexOutOfBoundsException: String index out of range: 487700285
        //	at java.lang.String.checkBounds(String.java:375)
        //	at java.lang.String.<init>(String.java:415)
        //	at com.sun.tools.jdi.PacketStream.readString(PacketStream.java:392)
        //	at com.sun.tools.jdi.JDWP$VirtualMachine$AllClassesWithGeneric$ClassInfo.<init>(JDWP.java:1644)
        LOG.info(e);
      }
      if (!list.isEmpty()) {
        final Set<ReferenceType> candidates = new HashSet<>();
        final ClassLoaderReference outerLoader = refType.classLoader();
        for (ReferenceType nested : list) {
          try {
            if (Objects.equals(outerLoader, nested.classLoader())) {
              candidates.add(nested);
            }
          }
          catch (ObjectCollectedException ignored) {
          }
        }

        if (!candidates.isEmpty()) {
          // keep only direct nested types
          // do not traverse all classes in vm, only the candidates list
          final Set<ReferenceType> nested2 = new HashSet<>();
          for (final ReferenceType candidate : candidates) {
            addNestedTypes(candidate, candidates, nested2);
          }
          candidates.removeAll(nested2);
        }

        nestedTypes = candidates.isEmpty() ? Collections.emptyList() : new ArrayList<>(candidates);
      }
      else {
        nestedTypes = Collections.emptyList();
      }
      myNestedClassesCache.put(refType, nestedTypes);
    }
    return nestedTypes;
  }

  /**
   * Check {@link ReferenceTypeImpl#nestedTypes()}
   */
  private static void addNestedTypes(ReferenceType base, Collection<ReferenceType> classes, Set<ReferenceType> nested) {
    String baseName = base.name();
    int baseLength = baseName.length();
    classes.forEach(type -> {
      String name = type.name();
      int length = name.length();
      if (length > baseLength && name.startsWith(baseName)) {
        char c = name.charAt(baseLength);
        if (c == '$' || c == '#') {
          nested.add(type);
        }
      }
    });
  }

  @Override
  public List<ReferenceType> allClasses() {
    List<ReferenceType> allClasses = myAllClasses;
    if (allClasses == null) {
      myAllClasses = allClasses = myVirtualMachine.allClasses();
    }
    return allClasses;
  }

  public String toString() {
    return myVirtualMachine.toString();
  }

  public void redefineClasses(Map<ReferenceType, byte[]> map) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    try {
      myVirtualMachine.redefineClasses(map);
    }
    finally {
      clearCaches();
    }
  }

  /**
   * @return a list of all ThreadReferenceProxies
   */
  public Collection<ThreadReferenceProxyImpl> allThreads() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if (myAllThreadsDirty) {
      myAllThreadsDirty = false;

      for (ThreadReference threadReference : myVirtualMachine.allThreads()) {
        getThreadReferenceProxy(threadReference, true); // add a proxy
      }
    }

    return new ArrayList<>(myAllThreads.values());
  }

  @TestOnly
  @ApiStatus.Internal
  public @NotNull Collection<ThreadReferenceProxyImpl> getEvenDirtyAllThreads() {
    return myAllThreads.values();
  }

  public CompletableFuture<Collection<ThreadReferenceProxyImpl>> allThreadsAsync() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if (myAllThreadsDirty) {
      return DebuggerUtilsAsync.allThreads(myVirtualMachine).thenApply(threads -> {
        DebuggerManagerThreadImpl.assertIsManagerThread();
        threads.forEach(thread -> getThreadReferenceProxy(thread, true)); // add proxies
        myAllThreadsDirty = false;
        return new ArrayList<>(myAllThreads.values());
      });
    }

    return CompletableFuture.completedFuture(new ArrayList<>(myAllThreads.values()));
  }

  public void threadStarted(ThreadReference thread) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    getThreadReferenceProxy(thread, true); // add a proxy
  }

  public void threadStopped(ThreadReference thread) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    myAllThreads.remove(thread);
  }

  public void suspend() {
    if (!canBeModified()) {
      return;
    }
    DebuggerManagerThreadImpl.assertIsManagerThread();
    myModelSuspendCount++;
    myVirtualMachine.suspend();
    clearCaches();
  }

  public void resume() {
    if (!canBeModified()) {
      return;
    }
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if (myModelSuspendCount <= 0) {
      myDebugProcess.logError("Negative global suspend count number!");
    }
    if (myModelSuspendCount > 0) {
      myModelSuspendCount--;
    }
    clearCaches();
    LOG.debug("before resume VM");
    DebuggerUtilsAsync.resume(myVirtualMachine).whenComplete((unused, throwable) -> {
      if (throwable != null && !(DebuggerUtilsAsync.unwrap(throwable) instanceof RejectedExecutionException)) {
        myDebugProcess.logError("Error on resume", throwable);
      }
      LOG.debug("VM resumed");
    });
    //logThreads();
  }

  /**
   * @return a list of threadGroupProxies
   */
  public List<ThreadGroupReferenceProxyImpl> topLevelThreadGroups() {
    return ContainerUtil.map(getVirtualMachine().topLevelThreadGroups(), this::getThreadGroupReferenceProxy);
  }

  public void threadGroupCreated(ThreadGroupReference threadGroupReference) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if (!isJ2ME()) {
      ThreadGroupReferenceProxyImpl proxy = new ThreadGroupReferenceProxyImpl(this, threadGroupReference);
      myThreadGroups.put(threadGroupReference, proxy);
    }
  }

  private boolean isJ2ME() {
    return isJ2ME(getVirtualMachine());
  }

  private static boolean isJ2ME(VirtualMachine virtualMachine) {
    return virtualMachine.version().startsWith("1.0");
  }

  public void threadGroupRemoved(ThreadGroupReference threadGroupReference) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    myThreadGroups.remove(threadGroupReference);
  }

  public EventRequestManager eventRequestManager() {
    return myVirtualMachine.eventRequestManager();
  }

  public VoidValue mirrorOfVoid() {
    return myVirtualMachine.mirrorOfVoid();
  }

  public BooleanValue mirrorOf(boolean b) {
    return myVirtualMachine.mirrorOf(b);
  }

  public ByteValue mirrorOf(byte b) {
    return myVirtualMachine.mirrorOf(b);
  }

  public CharValue mirrorOf(char c) {
    return myVirtualMachine.mirrorOf(c);
  }

  public ShortValue mirrorOf(short i) {
    return myVirtualMachine.mirrorOf(i);
  }

  public IntegerValue mirrorOf(int i) {
    return myVirtualMachine.mirrorOf(i);
  }

  public LongValue mirrorOf(long l) {
    return myVirtualMachine.mirrorOf(l);
  }

  public FloatValue mirrorOf(float v) {
    return myVirtualMachine.mirrorOf(v);
  }

  public DoubleValue mirrorOf(double v) {
    return myVirtualMachine.mirrorOf(v);
  }

  /**
   * Avoid using directly - use {@link com.intellij.debugger.impl.DebuggerUtilsImpl#mirrorOfString} instead
   */
  public StringReference mirrorOf(String s) {
    return myVirtualMachine.mirrorOf(s);
  }

  public StringReference mirrorOfStringLiteral(String s, ThrowableComputable<StringReference, EvaluateException> generator)
    throws EvaluateException {
    StringReference reference = myStringLiteralCache.get(s);
    if (reference != null && !reference.isCollected()) {
      return reference;
    }
    reference = generator.compute();
    myStringLiteralCache.put(s, reference);
    return reference;
  }

  public void dispose() {
    try {
      myVirtualMachine.dispose();
    }
    catch (UnsupportedOperationException e) {
      LOG.info(e);
    }
  }

  public void exit(int i) {
    myVirtualMachine.exit(i);
  }

  @Override
  public boolean canWatchFieldModification() {
    return myVirtualMachine.canWatchFieldModification();
  }

  @Override
  public boolean canWatchFieldAccess() {
    return myVirtualMachine.canWatchFieldAccess();
  }

  @Override
  public boolean canInvokeMethods() {
    return !isJ2ME();
  }

  @Override
  public boolean canGetBytecodes() {
    return myVirtualMachine.canGetBytecodes();
  }

  public boolean canGetConstantPool() {
    return myVirtualMachine.canGetConstantPool();
  }

  public boolean canGetSourceDebugExtension() { return myVirtualMachine.canGetSourceDebugExtension(); }

  public boolean canGetSyntheticAttribute() {
    return myVirtualMachine.canGetSyntheticAttribute();
  }

  public boolean canGetOwnedMonitorInfo() {
    return myVirtualMachine.canGetOwnedMonitorInfo();
  }

  public boolean canGetMonitorFrameInfo() {
    return myVirtualMachine.canGetMonitorFrameInfo();
  }

  public boolean canGetCurrentContendedMonitor() {
    return myVirtualMachine.canGetCurrentContendedMonitor();
  }

  public boolean canGetMonitorInfo() {
    return myVirtualMachine.canGetMonitorInfo();
  }

  public boolean canRedefineClasses() {
    return myVersionHigher_14 && myVirtualMachine.canRedefineClasses();
  }

  public boolean canPopFrames() {
    return myVersionHigher_14 && myVirtualMachine.canPopFrames();
  }

  public boolean canForceEarlyReturn() {
    return myVirtualMachine.canForceEarlyReturn();
  }

  public boolean canBeModified() {
    return !(myDebugProcess.getConnection() instanceof SAJDWPRemoteConnection) && myVirtualMachine.canBeModified();
  }

  @Override
  public final boolean versionHigher(String version) {
    return myVirtualMachine.version().compareTo(version) >= 0;
  }

  public boolean canGetMethodReturnValues() {
    return myVersionHigher_15 && myVirtualMachine.canGetMethodReturnValues();
  }

  public boolean canUseSourceNameFilters() {
    return myVirtualMachine.canUseSourceNameFilters();
  }

  public String description() {
    return myVirtualMachine.description();
  }

  public String version() {
    return myVirtualMachine.version();
  }

  public String name() {
    return myVirtualMachine.name();
  }

  @Nullable
  @Contract("null -> null; !null -> !null")
  public ThreadReferenceProxyImpl getThreadReferenceProxy(@Nullable ThreadReference thread) {
    return getThreadReferenceProxy(thread, false);
  }

  private ThreadReferenceProxyImpl getThreadReferenceProxy(@Nullable ThreadReference thread, boolean forceCache) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if (thread == null) {
      return null;
    }
    ThreadReferenceProxyImpl proxy = myAllThreads.computeIfAbsent(thread, t -> {
      // do not cache virtual threads
      if (!forceCache && thread instanceof ThreadReferenceImpl && ((ThreadReferenceImpl)thread).isVirtual()) {
        return null;
      }
      return new ThreadReferenceProxyImpl(this, t);
    });
    if (proxy == null) { // not cached
      proxy = new ThreadReferenceProxyImpl(this, thread);
    }
    return proxy;
  }

  public ThreadGroupReferenceProxyImpl getThreadGroupReferenceProxy(ThreadGroupReference group) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if (group == null) {
      return null;
    }

    ThreadGroupReferenceProxyImpl proxy = myThreadGroups.get(group);
    if (proxy == null) {
      if (!isJ2ME()) {
        proxy = new ThreadGroupReferenceProxyImpl(this, group);
        myThreadGroups.put(group, proxy);
      }
    }

    return proxy;
  }

  public boolean equals(Object obj) {
    LOG.assertTrue(obj instanceof VirtualMachineProxyImpl);
    return myVirtualMachine.equals(((VirtualMachineProxyImpl)obj).getVirtualMachine());
  }

  public int hashCode() {
    return myVirtualMachine.hashCode();
  }

  public void clearCaches() {
    LOG.debug("VM cleared");

    myAllClasses = null;

    if (!myNestedClassesCache.isEmpty()) {
      myNestedClassesCache = new HashMap<>(myNestedClassesCache.size());
    }
    //myAllThreadsDirty = true;
    myTimeStamp++;
  }

  @Override
  public int getCurrentTime() {
    return myTimeStamp;
  }

  @Override
  public DebugProcessImpl getDebugProcess() {
    return myDebugProcess;
  }

  public static boolean isCollected(ObjectReference reference) {
    try {
      return !isJ2ME(reference.virtualMachine()) && reference.isCollected();
    }
    catch (UnsupportedOperationException e) {
      LOG.info(e);
    }
    return false;
  }

  public boolean isPausePressed() {
    return myModelSuspendCount > 0;
  }

  public boolean isSuspended() {
    return ContainerUtil.exists(allThreads(), thread -> thread.getSuspendCount() != 0);
  }

  public int getModelSuspendCount() {
    return myModelSuspendCount;
  }

  public void addedSuspendAllContext() {
    myModelSuspendCount++;
  }

  public void resumedSuspendAllContext() {
    if (myModelSuspendCount <= 0) {
      myDebugProcess.logError("Negative global suspend count number!");
    }
    myModelSuspendCount--;
  }
}
