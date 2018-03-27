/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

/*
 * @author Eugene Zhuravlev
 */
package com.intellij.debugger.jdi;

import com.intellij.Patches;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.jdi.VirtualMachineProxy;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.HashMap;
import com.sun.jdi.*;
import com.sun.jdi.event.EventQueue;
import com.sun.jdi.request.EventRequestManager;
import com.sun.tools.jdi.JNITypeParser;
import com.sun.tools.jdi.TargetVM;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VirtualMachineProxyImpl implements JdiTimer, VirtualMachineProxy {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.jdi.VirtualMachineProxyImpl");
  private final DebugProcessImpl myDebugProcess;
  private final VirtualMachine myVirtualMachine;
  private int myTimeStamp = 0;
  private int myPausePressedCount = 0;

  // cached data
  private final Map<ObjectReference, ObjectReferenceProxyImpl>  myObjectReferenceProxies = new HashMap<>();
  private final Map<String, StringReference> myStringLiteralCache = new HashMap<>();

  @NotNull
  private final Map<ThreadReference, ThreadReferenceProxyImpl> myAllThreads = new ConcurrentHashMap<>();
  private final Map<ThreadGroupReference, ThreadGroupReferenceProxyImpl> myThreadGroups = new HashMap<>();
  private boolean myAllThreadsDirty = true;
  private List<ReferenceType> myAllClasses;
  private Map<ReferenceType, List<ReferenceType>> myNestedClassesCache = new HashMap<>();

  public final Throwable mySuspendLogger = new Throwable();
  private final boolean myVersionHigher_15;
  private final boolean myVersionHigher_14;

  public VirtualMachineProxyImpl(DebugProcessImpl debugProcess, @NotNull VirtualMachine virtualMachine) {
    myVirtualMachine = virtualMachine;
    myDebugProcess = debugProcess;

    myVersionHigher_15 = versionHigher("1.5");
    myVersionHigher_14 = myVersionHigher_15 || versionHigher("1.4");

    // avoid lazy-init for some properties: the following will pre-calculate values
    canRedefineClasses();
    canWatchFieldModification();
    canPopFrames();

    try {
      // this will cache classes inside JDI and enable faster search of classes later
      virtualMachine.allClasses();
    }
    catch (VMDisconnectedException e) {
      throw e;
    }
    catch (Throwable e) {
      // catch all exceptions in order not to break vm attach process
      // Example:
      // java.lang.IllegalArgumentException: Invalid JNI signature character ';'
      //  caused by some bytecode "optimizers" which break type signatures as a side effect.
      //  solution if you are using JAX-WS: add -Dcom.sun.xml.bind.v2.bytecode.ClassTailor.noOptimize=true to JVM args
      LOG.info(e);
    }

    virtualMachine.topLevelThreadGroups().forEach(this::threadGroupCreated);
  }

  @NotNull
  public VirtualMachine getVirtualMachine() {
    return myVirtualMachine;
  }

  static final class JNITypeParserReflect {
    static final Method typeNameToSignatureMethod;

    static {
      typeNameToSignatureMethod = ReflectionUtil.getDeclaredMethod(JNITypeParser.class, "typeNameToSignature", String.class);
      if (typeNameToSignatureMethod == null) {
        LOG.warn("Unable to find JNITypeParser.typeNameToSignature method");
      }
    }

    @Nullable
    static String typeNameToSignature(@NotNull String name) {
      if (typeNameToSignatureMethod != null) {
        try {
          return (String)typeNameToSignatureMethod.invoke(null, name);
        }
        catch (Exception ignored) {
        }
      }
      return null;
    }
  }

  public ClassesByNameProvider getClassesByNameProvider() {
    return this::classesByName;
  }

  public List<ReferenceType> classesByName(@NotNull String s) {
    return myVirtualMachine.classesByName(s);
  }

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
            if (outerLoader == null? nested.classLoader() == null : outerLoader.equals(nested.classLoader())) {
              candidates.add(nested);
            }
          }
          catch (ObjectCollectedException ignored) {
          }
        }

        if (!candidates.isEmpty()) {
          // keep only direct nested types
          final Set<ReferenceType> nested2 = new HashSet<>();
          for (final ReferenceType candidate : candidates) {
            nested2.addAll(nestedTypes(candidate));
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
        getThreadReferenceProxy(threadReference); // add a proxy
      }
    }

    return new ArrayList<>(myAllThreads.values());
  }

  public void threadStarted(ThreadReference thread) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    getThreadReferenceProxy(thread); // add a proxy
  }

  public void threadStopped(ThreadReference thread) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    myAllThreads.remove(thread);
  }

  public void suspend() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    myPausePressedCount++;
    myVirtualMachine.suspend();
    clearCaches();
  }

  public void resume() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if (myPausePressedCount > 0) {
      myPausePressedCount--;
    }
    clearCaches();
    LOG.debug("before resume VM");
    try {
      myVirtualMachine.resume();
    }
    catch (InternalException e) {
      // ok to ignore. Although documentation says it is safe to invoke resume() on running VM,
      // sometimes this leads to com.sun.jdi.InternalException: Unexpected JDWP Error: 13 (THREAD_NOT_SUSPENDED)
      LOG.info(e);
    }
    LOG.debug("VM resumed");
    //logThreads();
  }

  /**
   * @return a list of threadGroupProxies
   */
  public List<ThreadGroupReferenceProxyImpl> topLevelThreadGroups() {
    return StreamEx.of(getVirtualMachine().topLevelThreadGroups()).map(this::getThreadGroupReferenceProxy).toList();
  }

  public void threadGroupCreated(ThreadGroupReference threadGroupReference){
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if(!isJ2ME()) {
      ThreadGroupReferenceProxyImpl proxy = new ThreadGroupReferenceProxyImpl(this, threadGroupReference);
      myThreadGroups.put(threadGroupReference, proxy);
    }
  }

  public boolean isJ2ME() {
    return isJ2ME(getVirtualMachine());
  }

  private static boolean isJ2ME(VirtualMachine virtualMachine) {
    return virtualMachine.version().startsWith("1.0");
  }

  public void threadGroupRemoved(ThreadGroupReference threadGroupReference){
    DebuggerManagerThreadImpl.assertIsManagerThread();
    myThreadGroups.remove(threadGroupReference);
  }

  public EventQueue eventQueue() {
    return myVirtualMachine.eventQueue();
  }

  public EventRequestManager eventRequestManager() {
    return myVirtualMachine.eventRequestManager();
  }

  /**
   * @deprecated use {@link #mirrorOfVoid()} instead
   */
  @Deprecated
  public VoidValue mirrorOf() throws EvaluateException {
    return mirrorOfVoid();
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

  public Process process() {
    return myVirtualMachine.process();
  }

  public void dispose() {
    try {
      myVirtualMachine.dispose();
    }
    catch (UnsupportedOperationException e) {
      LOG.info(e);
    }
    finally {
      if (Patches.JDK_BUG_EVENT_CONTROLLER_LEAK) {
        // Memory leak workaround, see IDEA-163334
        TargetVM target = ReflectionUtil.getField(myVirtualMachine.getClass(), myVirtualMachine, TargetVM.class, "target");
        if (target != null) {
          Thread controller = ReflectionUtil.getField(target.getClass(), target, Thread.class, "eventController");
          if (controller != null) {
            controller.stop();
          }
        }
      }
    }
  }

  public void exit(int i) {
    myVirtualMachine.exit(i);
  }

  private final Capability myWatchFielsModification = new Capability() {
    protected boolean calcValue() {
      return myVirtualMachine.canWatchFieldModification();
    }
  };
  public boolean canWatchFieldModification() {
    return myWatchFielsModification.isAvailable();
  }

  private final Capability myWatchFieldAccess = new Capability() {
    protected boolean calcValue() {
      return myVirtualMachine.canWatchFieldAccess();
    }
  };
  public boolean canWatchFieldAccess() {
    return myWatchFieldAccess.isAvailable();
  }

  private final Capability myIsJ2ME = new Capability() {
    protected boolean calcValue() {
      return isJ2ME();
    }
  };
  public boolean canInvokeMethods() {
    return !myIsJ2ME.isAvailable();
  }

  private final Capability myGetBytecodes = new Capability() {
    protected boolean calcValue() {
      return myVirtualMachine.canGetBytecodes();
    }
  };
  public boolean canGetBytecodes() {
    return myGetBytecodes.isAvailable();
  }

  private final Capability myGetSyntheticAttribute = new Capability() {
    protected boolean calcValue() {
      return myVirtualMachine.canGetSyntheticAttribute();
    }
  };
  public boolean canGetSyntheticAttribute() {
    return myGetSyntheticAttribute.isAvailable();
  }

  private final Capability myGetOwnedMonitorInfo = new Capability() {
    protected boolean calcValue() {
      return myVirtualMachine.canGetOwnedMonitorInfo();
    }
  };
  public boolean canGetOwnedMonitorInfo() {
    return myGetOwnedMonitorInfo.isAvailable();
  }

  private final Capability myGetMonitorFrameInfo = new Capability() {
    protected boolean calcValue() {
      return myVirtualMachine.canGetMonitorFrameInfo();
    }
  };
  public boolean canGetMonitorFrameInfo() {
      return myGetMonitorFrameInfo.isAvailable();
  }
  
  private final Capability myGetCurrentContendedMonitor = new Capability() {
    protected boolean calcValue() {
      return myVirtualMachine.canGetCurrentContendedMonitor();
    }
  };
  public boolean canGetCurrentContendedMonitor() {
    return myGetCurrentContendedMonitor.isAvailable();
  }

  private final Capability myGetMonitorInfo = new Capability() {
    protected boolean calcValue() {
      return myVirtualMachine.canGetMonitorInfo();
    }
  };
  public boolean canGetMonitorInfo() {
    return myGetMonitorInfo.isAvailable();
  }

  private final Capability myUseInstanceFilters = new Capability() {
    protected boolean calcValue() {
      return myVersionHigher_14 && myVirtualMachine.canUseInstanceFilters();
    }
  };
  public boolean canUseInstanceFilters() {
    return myUseInstanceFilters.isAvailable();
  }

  private final Capability myRedefineClasses = new Capability() {
    protected boolean calcValue() {
      return myVersionHigher_14 && myVirtualMachine.canRedefineClasses();
    }
  };
  public boolean canRedefineClasses() {
    return myRedefineClasses.isAvailable();
  }

  private final Capability myAddMethod = new Capability() {
    protected boolean calcValue() {
      return myVersionHigher_14 && myVirtualMachine.canAddMethod();
    }
  };
  public boolean canAddMethod() {
    return myAddMethod.isAvailable();
  }

  private final Capability myUnrestrictedlyRedefineClasses = new Capability() {
    protected boolean calcValue() {
      return myVersionHigher_14 && myVirtualMachine.canUnrestrictedlyRedefineClasses();
    }
  };
  public boolean canUnrestrictedlyRedefineClasses() {
    return myUnrestrictedlyRedefineClasses.isAvailable();
  }

  private final Capability myPopFrames = new Capability() {
    protected boolean calcValue() {
      return myVersionHigher_14 && myVirtualMachine.canPopFrames();
    }
  };
  public boolean canPopFrames() {
    return myPopFrames.isAvailable();
  }

  private final Capability myForceEarlyReturn = new Capability() {
    protected boolean calcValue() {
      return myVirtualMachine.canForceEarlyReturn();
    }
  };
  public boolean canForceEarlyReturn() {
    return myForceEarlyReturn.isAvailable();
  }

  private final Capability myCanGetInstanceInfo = new Capability() {
    protected boolean calcValue() {
      if (!myVersionHigher_15) {
        return false;
      }
      try {
        final Method method = VirtualMachine.class.getMethod("canGetInstanceInfo");
        return (Boolean)method.invoke(myVirtualMachine);
      }
      catch (NoSuchMethodException ignored) {
      }
      catch (IllegalAccessException | InvocationTargetException e) {
        LOG.error(e);
      }
      return false;
    }
  };
  public boolean canGetInstanceInfo() {
    return myCanGetInstanceInfo.isAvailable();
  }

  public final boolean versionHigher(String version) {
    return myVirtualMachine.version().compareTo(version) >= 0;
  }

  private final Capability myGetSourceDebugExtension = new Capability() {
    protected boolean calcValue() {
      return myVersionHigher_14 && myVirtualMachine.canGetSourceDebugExtension();
    }
  };
  public boolean canGetSourceDebugExtension() {
    return myGetSourceDebugExtension.isAvailable();
  }

  private final Capability myRequestVMDeathEvent = new Capability() {
    protected boolean calcValue() {
      return myVersionHigher_14 && myVirtualMachine.canRequestVMDeathEvent();
    }
  };
  public boolean canRequestVMDeathEvent() {
    return myRequestVMDeathEvent.isAvailable();
  }

  private final Capability myGetMethodReturnValues = new Capability() {
    protected boolean calcValue() {
      if (myVersionHigher_15) {
        //return myVirtualMachine.canGetMethodReturnValues();
        try {
          //noinspection HardCodedStringLiteral
          final Method method = VirtualMachine.class.getDeclaredMethod("canGetMethodReturnValues");
          final Boolean rv = (Boolean)method.invoke(myVirtualMachine);
          return rv.booleanValue();
        }
        catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException ignored) {
        }
      }
      return false;
    }
  };
  public boolean canGetMethodReturnValues() {
    return myGetMethodReturnValues.isAvailable();
  }

  public String getDefaultStratum() {
    return myVersionHigher_14 ? myVirtualMachine.getDefaultStratum() : null;
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

  public void setDebugTraceMode(int i) {
    myVirtualMachine.setDebugTraceMode(i);
  }

  @Nullable
  @Contract("null -> null; !null -> !null")
  public ThreadReferenceProxyImpl getThreadReferenceProxy(@Nullable ThreadReference thread) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if (thread == null) {
      return null;
    }

    return myAllThreads.computeIfAbsent(thread, t -> new ThreadReferenceProxyImpl(this, t));
  }

  public ThreadGroupReferenceProxyImpl getThreadGroupReferenceProxy(ThreadGroupReference group) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if(group == null) {
      return null;
    }

    ThreadGroupReferenceProxyImpl proxy = myThreadGroups.get(group);
    if(proxy == null) {
      if(!myIsJ2ME.isAvailable()) {
        proxy = new ThreadGroupReferenceProxyImpl(this, group);
        myThreadGroups.put(group, proxy);
      }
    }

    return proxy;
  }

  public ObjectReferenceProxyImpl getObjectReferenceProxy(ObjectReference objectReference) {
    if (objectReference != null) {
      if (objectReference instanceof ThreadReference) {
        return getThreadReferenceProxy((ThreadReference)objectReference);
      }
      else if (objectReference instanceof ThreadGroupReference) {
        return getThreadGroupReferenceProxy((ThreadGroupReference)objectReference);
      }
      else {
        ObjectReferenceProxyImpl proxy = myObjectReferenceProxies.get(objectReference);
        if (proxy == null) {
          if (objectReference instanceof StringReference) {
            proxy = new StringReferenceProxy(this, (StringReference)objectReference);
          }
          else {
            proxy = new ObjectReferenceProxyImpl(this, objectReference);
          }
          myObjectReferenceProxies.put(objectReference, proxy);
        }
        return proxy;
      }
    }
    return null;
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

  public int getCurrentTime() {
    return myTimeStamp;
  }

  public DebugProcess getDebugProcess() {
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

  public String getResumeStack() {
    return StringUtil.getThrowableText(mySuspendLogger);
  }

  public boolean isPausePressed() {
    return myPausePressedCount > 0;
  }

  public boolean isSuspended() {
    return allThreads().stream().anyMatch(thread -> thread.getSuspendCount() != 0);
  }

  public void logThreads() {
    if (LOG.isDebugEnabled()) {
      for (ThreadReferenceProxyImpl thread : allThreads()) {
        if (!thread.isCollected()) {
          LOG.debug("suspends " + thread + " " + thread.getSuspendCount() + " " + thread.isSuspended());
        }
      }
    }
  }


  private abstract static class Capability {
    private ThreeState myValue = ThreeState.UNSURE;

    public final boolean isAvailable() {
      if (myValue == ThreeState.UNSURE) {
        try {
          myValue = ThreeState.fromBoolean(calcValue());
        }
        catch (VMDisconnectedException e) {
          LOG.info(e);
          myValue = ThreeState.NO;
        }
      }
      return myValue.toBoolean();
    }

    protected abstract boolean calcValue();
  }

}
