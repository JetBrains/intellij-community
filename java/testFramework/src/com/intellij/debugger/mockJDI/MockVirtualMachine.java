/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.mockJDI;

import com.intellij.debugger.mockJDI.types.*;
import com.intellij.debugger.mockJDI.values.*;
import com.intellij.psi.PsiClass;
import com.sun.jdi.*;
import com.sun.jdi.event.EventQueue;
import com.sun.jdi.request.EventRequestManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.ClassReader;

import java.util.*;

public class MockVirtualMachine implements VirtualMachine {
  private final Map<Class<?>, MockReferenceType> myCachedClasses = new HashMap<>();
  private final Map<PsiClass, MockPsiReferenceType> myCachedPsiClasses = new HashMap<>();
  private final List<ReferenceType> myClasses = new ArrayList<>();
  private final MockType myBooleanType = new MockBooleanType(this);
  private final MockType myIntType = new MockIntegerType(this);
  private final MockType myLongType = new MockLongType(this);
  private final MockType myShortType = new MockShortType(this);
  private final MockType myVoidType = new MockVoidType(this);
  private final Map<Class, ClassReader> myClass2Reader = new HashMap<>();
  private final List<ThreadReference> myThreads = Collections.singletonList(new MockThreadReference(this, Thread.currentThread()));

  public MockVirtualMachine() {
  }

  public void addClass(Class aClass, byte @Nullable [] bytes) {
    myClasses.add(createReferenceType(aClass));
    if (bytes != null) {
      myClass2Reader.put(aClass, new ClassReader(bytes));
    }
  }

  public ClassReader getReader(Class aClass) {
    return myClass2Reader.get(aClass);
  }

  @Override
  public List<ReferenceType> classesByName(String string) {
    ArrayList<ReferenceType> referenceTypes = new ArrayList<>();
    try {
      Class<?> aClass = Class.forName(string);
      referenceTypes.add(createReferenceType(aClass));
    }
    catch (ClassNotFoundException ignored) {
    }
    return referenceTypes;
  }

  @Override
  public VoidValue mirrorOfVoid() {
    return new MockVoidValue(this);
  }

  @Override
  public List<ReferenceType> allClasses() {
    return myClasses;
  }

  @Override
  public void redefineClasses(Map map) {
    throw new UnsupportedOperationException("Not implemented: \"redefineClasses\" in " + getClass().getName());
  }

  @Override
  public List<ThreadReference> allThreads() {
    return myThreads;
  }

  @Override
  public void suspend() {
    throw new UnsupportedOperationException("Not implemented: \"suspend\" in " + getClass().getName());
  }

  @Override
  public void resume() {
    throw new UnsupportedOperationException("Not implemented: \"resume\" in " + getClass().getName());
  }

  @Override
  public List<ThreadGroupReference> topLevelThreadGroups() {
    return Collections.emptyList();
  }

  @Override
  public EventQueue eventQueue() {
    throw new UnsupportedOperationException("Not implemented: \"eventQueue\" in " + getClass().getName());
  }

  @Override
  public EventRequestManager eventRequestManager() {
    throw new UnsupportedOperationException("Not implemented: \"eventRequestManager\" in " + getClass().getName());
  }

  @Override
  public BooleanValue mirrorOf(boolean b) {
    return new MockBooleanValue(this, b);
  }

  @Override
  public ByteValue mirrorOf(byte b) {
    throw new UnsupportedOperationException("Not implemented: \"mirrorOf\" in " + getClass().getName());
  }

  @Override
  public CharValue mirrorOf(char c) {
    throw new UnsupportedOperationException("Not implemented: \"mirrorOf\" in " + getClass().getName());
  }

  @Override
  public ShortValue mirrorOf(short i) {
    throw new UnsupportedOperationException("Not implemented: \"mirrorOf\" in " + getClass().getName());
  }

  @Override
  public IntegerValue mirrorOf(int i) {
    return new MockIntegerValue(this, i);
  }

  @Override
  public LongValue mirrorOf(long l) {
    throw new UnsupportedOperationException("Not implemented: \"mirrorOf\" in " + getClass().getName());
  }

  @Override
  public FloatValue mirrorOf(float v) {
    throw new UnsupportedOperationException("Not implemented: \"mirrorOf\" in " + getClass().getName());
  }

  @Override
  public DoubleValue mirrorOf(double v) {
    throw new UnsupportedOperationException("Not implemented: \"mirrorOf\" in " + getClass().getName());
  }

  @Override
  public StringReference mirrorOf(String string) {
    return new MockStringReference(this, string);
  }

  @Override
  public Process process() {
    throw new UnsupportedOperationException("Not implemented: \"process\" in " + getClass().getName());
  }

  @Override
  public void dispose() {
    throw new UnsupportedOperationException("Not implemented: \"dispose\" in " + getClass().getName());
  }

  @Override
  public void exit(int i) {
    throw new UnsupportedOperationException("Not implemented: \"exit\" in " + getClass().getName());
  }

  @Override
  public boolean canWatchFieldModification() {
    return false;
  }

  @Override
  public boolean canWatchFieldAccess() {
    throw new UnsupportedOperationException("Not implemented: \"canWatchFieldAccess\" in " + getClass().getName());
  }

  @Override
  public boolean canGetBytecodes() {
    throw new UnsupportedOperationException("Not implemented: \"canGetBytecodes\" in " + getClass().getName());
  }

  @Override
  public boolean canGetSyntheticAttribute() {
    throw new UnsupportedOperationException("Not implemented: \"canGetSyntheticAttribute\" in " + getClass().getName());
  }

  @Override
  public boolean canGetOwnedMonitorInfo() {
    throw new UnsupportedOperationException("Not implemented: \"canGetOwnedMonitorInfo\" in " + getClass().getName());
  }

  @Override
  public boolean canGetCurrentContendedMonitor() {
    throw new UnsupportedOperationException("Not implemented: \"canGetCurrentContendedMonitor\" in " + getClass().getName());
  }

  @Override
  public boolean canGetMonitorInfo() {
    throw new UnsupportedOperationException("Not implemented: \"canGetMonitorInfo\" in " + getClass().getName());
  }

  @Override
  public boolean canUseInstanceFilters() {
    throw new UnsupportedOperationException("Not implemented: \"canUseInstanceFilters\" in " + getClass().getName());
  }

  @Override
  public boolean canRedefineClasses() {
    return false;
  }

  @Override
  public boolean canAddMethod() {
    throw new UnsupportedOperationException("Not implemented: \"canAddMethod\" in " + getClass().getName());
  }

  @Override
  public boolean canUnrestrictedlyRedefineClasses() {
    throw new UnsupportedOperationException("Not implemented: \"canUnrestrictedlyRedefineClasses\" in " + getClass().getName());
  }

  @Override
  public boolean canPopFrames() {
    return false;
  }

  @Override
  public boolean canGetSourceDebugExtension() {
    throw new UnsupportedOperationException("Not implemented: \"canGetSourceDebugExtension\" in " + getClass().getName());
  }

  @Override
  public boolean canRequestVMDeathEvent() {
    throw new UnsupportedOperationException("Not implemented: \"canRequestVMDeathEvent\" in " + getClass().getName());
  }

  @Override
  public boolean canBeModified() {
    return true;
  }

  @Override
  public void setDefaultStratum(String string) {
    throw new UnsupportedOperationException("Not implemented: \"setDefaultStratum\" in " + getClass().getName());
  }

  @Override
  public String getDefaultStratum() {
    return null;
  }

  @Override
  public String description() {
    throw new UnsupportedOperationException("Not implemented: \"description\" in " + getClass().getName());
  }

  @Override
  public String version() {
    return "1.5";
  }

  @Override
  public String name() {
    return "MockVirtualMachine";
  }

  @Override
  public void setDebugTraceMode(int i) {
    throw new UnsupportedOperationException("Not implemented: \"setDebugTraceMode\" in " + getClass().getName());
  }

  @Override
  public VirtualMachine virtualMachine() {
    return this;
  }

  public MockInterfaceType createInterfaceType(Class<?> type) {
    return (MockInterfaceType)createReferenceType(type);
  }

  public MockReferenceType createReferenceType(Class<?> type) {
    MockReferenceType refType = myCachedClasses.get(type);
    // Do not use computeIfAbsent as concurrent update is possible
    if (refType == null) {
      refType = type.isArray() ? new MockArrayType(MockType.createType(this, type.getComponentType())) :
                type.isInterface() ? new MockInterfaceType(this, type) : new MockClassType(this, type);
      myCachedClasses.put(type, refType);
    }
    return refType;
  }

  public MockPsiReferenceType createReferenceType(@NotNull PsiClass psiClass) {
    return myCachedPsiClasses
      .computeIfAbsent(psiClass, c -> c.isInterface() ? new MockPsiInterfaceType(this, c) : new MockPsiClassType(this, c));
  }

  public MockType getBooleanType() {
    return myBooleanType;
  }

  public MockType getIntType() {
    return myIntType;
  }

  public MockType getLongType() {
    return myLongType;
  }

  public MockType getShortType() {
    return myShortType;
  }

  @Override
  public boolean canGetMethodReturnValues() {
    throw new UnsupportedOperationException("Not implemented: \"canGetMethodReturnValues\" in " + getClass().getName());
  }

  @Override
  public boolean canGetInstanceInfo() {
    throw new UnsupportedOperationException("Not implemented: \"canGetInstanceInfo\" in " + getClass().getName());
  }

  @Override
  public boolean canUseSourceNameFilters() {
    throw new UnsupportedOperationException("Not implemented: \"canUseSourceNameFilters\" in " + getClass().getName());
  }

  @Override
  public boolean canForceEarlyReturn() {
    throw new UnsupportedOperationException("Not implemented: \"canForceEarlyReturn\" in " + getClass().getName());
  }

  @Override
  public boolean canRequestMonitorEvents() {
    throw new UnsupportedOperationException("Not implemented: \"canRequestMonitorEvents\" in " + getClass().getName());
  }

  @Override
  public boolean canGetMonitorFrameInfo() {
    throw new UnsupportedOperationException("Not implemented: \"canGetMonitorFrameInfo\" in " + getClass().getName());
  }

  @Override
  public boolean canGetClassFileVersion() {
    throw new UnsupportedOperationException("Not implemented: \"canGetClassFileVersion\" in " + getClass().getName());
  }

  @Override
  public boolean canGetConstantPool() {
    throw new UnsupportedOperationException("Not implemented: \"canGetConstantPool\" in " + getClass().getName());
  }

  @Override
  public long[] instanceCounts(List<? extends ReferenceType> list) {
    throw new UnsupportedOperationException("Not implemented: \"instanceCounts\" in " + getClass().getName());
  }

  public MockType getVoidType() {
    return myVoidType;
  }
}
