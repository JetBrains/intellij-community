/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * @author Eugene Zhuravlev
 */
package com.intellij.debugger.jdi;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.jdi.VirtualMachineProxy;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.HashMap;
import com.sun.jdi.*;
import com.sun.jdi.event.EventQueue;
import com.sun.jdi.request.EventRequestManager;
import com.sun.tools.jdi.VoidValueImpl;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class VirtualMachineProxyImpl implements JdiTimer, VirtualMachineProxy {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.jdi.VirtualMachineProxyImpl");
  private final DebugProcessImpl myDebugProcess;
  private final VirtualMachine myVirtualMachine;
  private int myTimeStamp = 0;
  private int myPausePressedCount = 0;

  // cached data
  private final Map<ObjectReference, ObjectReferenceProxyImpl>  myObjectReferenceProxies = new HashMap<ObjectReference, ObjectReferenceProxyImpl>();
  private Map<ThreadReference, ThreadReferenceProxyImpl>  myAllThreads = new HashMap<ThreadReference, ThreadReferenceProxyImpl>();
  private final Map<ThreadGroupReference, ThreadGroupReferenceProxyImpl> myThreadGroups = new HashMap<ThreadGroupReference, ThreadGroupReferenceProxyImpl>();
  private boolean myAllThreadsDirty = true;
  private List<ReferenceType> myAllClasses;
  private Map<ReferenceType, List<ReferenceType>> myNestedClassesCache = new HashMap<ReferenceType, List<ReferenceType>>();

  public Throwable mySuspendLogger = new Throwable();
  private final boolean myVersionHigher_15;
  private final boolean myVersionHigher_14;

  public VirtualMachineProxyImpl(DebugProcessImpl debugProcess, @NotNull VirtualMachine virtualMachine) {
    myVirtualMachine = virtualMachine;
    myDebugProcess = debugProcess;

    myVersionHigher_15 = versionHigher("1.5");
    myVersionHigher_14 = myVersionHigher_15 || versionHigher("1.4");

    List<ThreadGroupReference> groups = virtualMachine.topLevelThreadGroups();
    for (ThreadGroupReference threadGroupReference : groups) {
      threadGroupCreated(threadGroupReference);
    }
  }

  public VirtualMachine getVirtualMachine() {
    return myVirtualMachine;
  }

  public List<ReferenceType> classesByName(String s) {
    return myVirtualMachine.classesByName(s);
  }

  public List<ReferenceType> nestedTypes(ReferenceType refType) {
    List<ReferenceType> nestedTypes = myNestedClassesCache.get(refType);
    if (nestedTypes == null) {
      final List<ReferenceType> list = refType.nestedTypes();
      nestedTypes = new ArrayList<ReferenceType>(list.size());
      final ClassLoaderReference outerLoader = refType.classLoader();
      for (ReferenceType type : list) {
        if (outerLoader == null? type.classLoader() == null : outerLoader.equals(type.classLoader())) {
          nestedTypes.add(type);
        }
      }
      myNestedClassesCache.put(refType, nestedTypes);
    }
    return nestedTypes;
  }

  public List<ReferenceType> allClasses() {
    if (myAllClasses == null) {
      myAllClasses = myVirtualMachine.allClasses();
    }
    return myAllClasses;
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
    if(myAllThreadsDirty) {
      myAllThreadsDirty = false;

      final List<ThreadReference> currentThreads = myVirtualMachine.allThreads();
      final Map<ThreadReference, ThreadReferenceProxyImpl> result = new HashMap<ThreadReference, ThreadReferenceProxyImpl>();

      for (final ThreadReference threadReference : currentThreads) {
        ThreadReferenceProxyImpl proxy = myAllThreads.get(threadReference);
        if(proxy == null) {
          proxy = new ThreadReferenceProxyImpl(this, threadReference);
        }
        result.put(threadReference, proxy);
      }
      myAllThreads = result;
    }

    return myAllThreads.values();
  }

  public void threadStarted(ThreadReference thread) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    final Map<ThreadReference, ThreadReferenceProxyImpl> allThreads = myAllThreads;
    if (allThreads != null && !allThreads.containsKey(thread)) {
      allThreads.put(thread, new ThreadReferenceProxyImpl(this, thread));
    }
  }

  public void threadStopped(ThreadReference thread) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    final Map<ThreadReference, ThreadReferenceProxyImpl> allThreads = myAllThreads;
    if (allThreads != null) {
      allThreads.remove(thread);
    }
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
    if (LOG.isDebugEnabled()) {
      LOG.debug("before resume VM");
    }
    try {
      myVirtualMachine.resume();
    }
    catch (InternalException e) {
      // ok to ignore. Although documentation says it is safe to invoke resume() on running VM,
      // sometimes this leads to com.sun.jdi.InternalException: Unexpected JDWP Error: 13 (THREAD_NOT_SUSPENDED)
      LOG.info(e);
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("VM resumed");
    }
    //logThreads();
  }

  /**
   * @return a list of threadGroupProxies
   */
  public List<ThreadGroupReferenceProxyImpl> topLevelThreadGroups() {
    List<ThreadGroupReference> list = getVirtualMachine().topLevelThreadGroups();

    List<ThreadGroupReferenceProxyImpl> result = new ArrayList<ThreadGroupReferenceProxyImpl>(list.size());

    for (ThreadGroupReference threadGroup : list) {
      result.add(getThreadGroupReferenceProxy(threadGroup));
    }

    return result;
  }

  public void threadGroupCreated(ThreadGroupReference threadGroupReference){
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
    myThreadGroups.remove(threadGroupReference);
  }

  public EventQueue eventQueue() {
    return myVirtualMachine.eventQueue();
  }

  public EventRequestManager eventRequestManager() {
    return myVirtualMachine.eventRequestManager();
  }

  public VoidValue mirrorOf() throws EvaluateException {
    try {
      Constructor<VoidValueImpl> constructor = VoidValueImpl.class.getDeclaredConstructor(new Class[]{VirtualMachine.class});
      constructor.setAccessible(true);
      return constructor.newInstance(myVirtualMachine);
    }
    catch (NoSuchMethodException e) {
      LOG.error(e);
    }
    catch (IllegalAccessException e) {
      LOG.error(e);
    }
    catch (InvocationTargetException e) {
      LOG.error(e);
    }
    catch (InstantiationException e) {
      LOG.error(e);
    }
    throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("error.cannot.create.void.value"));
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

  public Process process() {
    return myVirtualMachine.process();
  }

  public void dispose() {
    myVirtualMachine.dispose();
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

  private final Capability myInvokeMethods = new Capability() {
    protected boolean calcValue() {
      return isJ2ME();
    }
  };
  public boolean canInvokeMethods() {
    return myInvokeMethods.isAvailable();
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
      catch (IllegalAccessException e) {
        LOG.error(e);
      }
      catch (InvocationTargetException e) {
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
        catch (NoSuchMethodException ignored) {
        }
        catch (IllegalAccessException ignored) {
        }
        catch (InvocationTargetException ignored) {
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

  public ThreadReferenceProxyImpl getThreadReferenceProxy(ThreadReference thread) {
    if(thread == null) {
      return null;
    }

    ThreadReferenceProxyImpl proxy = myAllThreads.get(thread);
    if(proxy == null) {
      proxy = new ThreadReferenceProxyImpl(this, thread);
      myAllThreads.put(thread, proxy);
    }

    return proxy;
  }

  public ThreadGroupReferenceProxyImpl getThreadGroupReferenceProxy(ThreadGroupReference group) {
    if(group == null) {
      return null;
    }

    ThreadGroupReferenceProxyImpl proxy = myThreadGroups.get(group);
    if(proxy == null) {
      if(!isJ2ME()) {
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
    if (LOG.isDebugEnabled()) {
      LOG.debug("VM cleared");
    }

    myAllClasses = null;
    if (!myNestedClassesCache.isEmpty()) {
      myNestedClassesCache = new HashMap<ReferenceType, List<ReferenceType>>(myNestedClassesCache.size());
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
    return !isJ2ME(reference.virtualMachine()) && reference.isCollected();
  }

  public String getResumeStack() {
    return StringUtil.getThrowableText(mySuspendLogger);
  }

  public boolean isPausePressed() {
    return myPausePressedCount > 0;
  }

  public boolean isSuspended() {
    for (ThreadReferenceProxyImpl thread : allThreads()) {
      if (thread.getSuspendCount() != 0) {
        return true;
      }
    }
    return false;
  }

  public void logThreads() {
    if(LOG.isDebugEnabled()) {
      for (ThreadReferenceProxyImpl thread : allThreads()) {
        if (!thread.isCollected()) {
          LOG.debug("suspends " + thread + " " + thread.getSuspendCount() + " " + thread.isSuspended());
        }
      }
    }
  }


  private abstract static class Capability {
    Boolean myValue = null;

    public final boolean isAvailable() {
      if (myValue == null) {
        try {
          myValue = Boolean.valueOf(calcValue());
        }
        catch (VMDisconnectedException e) {
          LOG.info(e);
          myValue = Boolean.FALSE;
        }
      }
      return myValue.booleanValue();
    }

    protected abstract boolean calcValue();
  }

}
