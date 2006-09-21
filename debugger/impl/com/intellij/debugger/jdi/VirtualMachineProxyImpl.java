/*
 * @author Eugene Zhuravlev
 */
package com.intellij.debugger.jdi;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.jdi.VirtualMachineProxy;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.HashMap;
import com.sun.jdi.*;
import com.sun.jdi.event.EventQueue;
import com.sun.jdi.request.EventRequestManager;
import com.sun.tools.jdi.VoidValueImpl;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

public class VirtualMachineProxyImpl implements JdiTimer, VirtualMachineProxy {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.jdi.VirtualMachineProxyImpl");
  private final DebugProcessImpl myDebugProcess;
  private final VirtualMachine myVirtualMachine;
  private int myTimeStamp = 0;
  private int myPausePressedCount = 0;

  // cached data
  private Map<ObjectReference, ObjectReferenceProxyImpl>  myObjectReferenceProxies = new HashMap<ObjectReference, ObjectReferenceProxyImpl>();
  private Map<ThreadReference, ThreadReferenceProxyImpl>  myAllThreads = new com.intellij.util.containers.HashMap<ThreadReference, ThreadReferenceProxyImpl>();
  private Map<ThreadGroupReference, ThreadGroupReferenceProxyImpl> myThreadGroups = new HashMap<ThreadGroupReference, ThreadGroupReferenceProxyImpl>();
  private boolean myAllThreadsDirty = true;
  private List<ReferenceType> myAllClasses;
  private Map<ReferenceType, List<ReferenceType>> myNestedClassesCache = new HashMap<ReferenceType, List<ReferenceType>>();

  public Throwable mySuspendLogger = new Throwable();

  public VirtualMachineProxyImpl(DebugProcessImpl debugProcess, VirtualMachine virtualMachine) {
    LOG.assertTrue(virtualMachine != null);
    myVirtualMachine = virtualMachine;
    myDebugProcess = debugProcess;

    List groups = virtualMachine.topLevelThreadGroups();
    for (Iterator it = groups.iterator(); it.hasNext();) {
      ThreadGroupReference threadGroupReference = (ThreadGroupReference)it.next();
      threadGroupCreated(threadGroupReference);
    }
  }

  public VirtualMachine getVirtualMachine() {
    return myVirtualMachine;
  }

  public List<ReferenceType> classesByName(String s) {
    return (List<ReferenceType>)myVirtualMachine.classesByName(s);
  }

  public List<ReferenceType> nestedTypes(ReferenceType refType) {
    List<ReferenceType> nestedTypes = myNestedClassesCache.get(refType);
    if (nestedTypes == null) {
      nestedTypes = (List<ReferenceType>)refType.nestedTypes();
      myNestedClassesCache.put(refType, nestedTypes);
    }
    return nestedTypes;
  }

  public List<ReferenceType> allClasses() {
    if (myAllClasses == null) {
      myAllClasses = (List<ReferenceType>)myVirtualMachine.allClasses();
    }
    return myAllClasses;
  }

  public String toString() {
    return myVirtualMachine.toString();
  }

  public void redefineClasses(Map map) {
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

      List threads = myVirtualMachine.allThreads();
      Map<ThreadReference, ThreadReferenceProxyImpl> result = new HashMap<ThreadReference, ThreadReferenceProxyImpl>();

      for (Iterator iterator = threads.iterator(); iterator.hasNext();) {
        ThreadReference threadReference = (ThreadReference)iterator.next();

        ThreadReferenceProxyImpl threadReferenceProxy = getThreadReferenceProxy(threadReference);
        LOG.assertTrue(threadReferenceProxy != null);

        result.put(threadReference, threadReferenceProxy);
      }
      myAllThreads = result;
    }

    return myAllThreads.values();
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
    myVirtualMachine.resume();
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

    for (Iterator<ThreadGroupReference> iterator = list.iterator(); iterator.hasNext();) {
      ThreadGroupReference threadGroup = iterator.next();
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
      return constructor.newInstance(new Object[] {myVirtualMachine});
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

  public boolean canWatchFieldModification() {
    return myVirtualMachine.canWatchFieldModification();
  }

  public boolean canWatchFieldAccess() {
    return myVirtualMachine.canWatchFieldAccess();
  }

  public boolean canInvokeMethods() {
    return isJ2ME();
  }

  public boolean canGetBytecodes() {
    return myVirtualMachine.canGetBytecodes();
  }

  public boolean canGetSyntheticAttribute() {
    return myVirtualMachine.canGetSyntheticAttribute();
  }

  public boolean canGetOwnedMonitorInfo() {
    return myVirtualMachine.canGetOwnedMonitorInfo();
  }

  public boolean canGetCurrentContendedMonitor() {
    return myVirtualMachine.canGetCurrentContendedMonitor();
  }

  public boolean canGetMonitorInfo() {
    return myVirtualMachine.canGetMonitorInfo();
  }

  public boolean canUseInstanceFilters() {
    if (versionHigher("1.4")) {
      return myVirtualMachine.canUseInstanceFilters();
    } else
      return false;
  }

  public boolean canRedefineClasses() {
    if (versionHigher("1.4")) {
      return myVirtualMachine.canRedefineClasses();
    }
    return false;
  }

  public boolean canAddMethod() {
    if (versionHigher("1.4")) {
      return myVirtualMachine.canAddMethod();
    } else
      return false;
  }

  public boolean canUnrestrictedlyRedefineClasses() {
    if (versionHigher("1.4")) {
      return myVirtualMachine.canUnrestrictedlyRedefineClasses();
    } else
      return false;
  }

  public boolean canPopFrames() {
    if (versionHigher("1.4")) {
      return myVirtualMachine.canPopFrames();
    }
    return false;
  }

  public boolean versionHigher(String version) {
    return myVirtualMachine.version().compareTo(version) >= 0;
  }

  public boolean canGetSourceDebugExtension() {
    if (versionHigher("1.4")) {
      return myVirtualMachine.canGetSourceDebugExtension();
    } else
      return false;
  }

  public boolean canRequestVMDeathEvent() {
    if(versionHigher("1.4")){
      return myVirtualMachine.canRequestVMDeathEvent();
    } else
      return false;
  }

  public String getDefaultStratum() {
    if(versionHigher("1.4")){
      return myVirtualMachine.getDefaultStratum();
    } else
      return null;
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
    if(thread == null) return null;

    ThreadReferenceProxyImpl proxy = myAllThreads.get(thread);
    if(proxy == null) {
      proxy = new ThreadReferenceProxyImpl(this, thread);
      myAllThreads.put(thread, proxy);
    }

    return proxy;
  }

  public ThreadGroupReferenceProxyImpl getThreadGroupReferenceProxy(ThreadGroupReference group) {
    if(group == null) return null;

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
        ObjectReferenceProxyImpl proxy = (ObjectReferenceProxyImpl)myObjectReferenceProxies.get(objectReference);
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
    else {
      return null;
    }
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
    if (myNestedClassesCache.size() > 0) {
      myNestedClassesCache = new HashMap<ReferenceType, List<ReferenceType>>(myNestedClassesCache.size());
    }
    myAllThreadsDirty = true;
    myTimeStamp++;
  }

  public int getCurrentTime() {
    return myTimeStamp;
  }

  public DebugProcessImpl getDebugProcess() {
    return myDebugProcess;
  }

  public static boolean isCollected(ObjectReference reference) {
    if(isJ2ME(reference.virtualMachine())){
      return false;
    }
    else {
      return reference.isCollected();
    }
  }

  public String getResumeStack() {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    mySuspendLogger.printStackTrace(pw);
    pw.flush();
    return sw.getBuffer().toString();
  }

  public boolean isPausePressed() {
    return myPausePressedCount > 0;
  }

  public boolean isSuspended() {
    //logThreads();
    for (Iterator iterator = allThreads().iterator(); iterator.hasNext();) {
      ThreadReferenceProxyImpl thread = (ThreadReferenceProxyImpl)iterator.next();
      if(thread.getSuspendCount() == 0) {
        return false;
      }
    }
    return true;
  }

  public void logThreads() {
    if(LOG.isDebugEnabled()) {
      for (Iterator iterator = allThreads().iterator(); iterator.hasNext();) {
        ThreadReferenceProxyImpl thread = (ThreadReferenceProxyImpl)iterator.next();
        if (!thread.isCollected()) {
          LOG.debug("suspends " + thread + " " + thread.getSuspendCount() + " " + thread.isSuspended());
        }
      }
    }
  }




}
