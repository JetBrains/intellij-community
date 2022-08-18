/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.mockJDI.values;

import com.intellij.debugger.mockJDI.MockVirtualMachine;
import com.intellij.debugger.mockJDI.members.MockField;
import com.intellij.debugger.mockJDI.members.MockMethod;
import com.intellij.debugger.mockJDI.types.MockClassType;
import com.sun.jdi.*;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;

public class MockObjectReference extends MockValue implements ObjectReference {
  private final Object myObject;

  protected MockObjectReference(final MockVirtualMachine virtualMachine, Object object) {
    super(virtualMachine);
    myObject = object;
  }

  @Override
  public ReferenceType referenceType() {
    return myVirtualMachine.createReferenceType(myObject.getClass());
  }

  @Override
  public Type type() {
    return referenceType();
  }

  @Override
  public Value getValue(Field field) {
    try {
      java.lang.reflect.Field refField = ((MockField) field).getField();
      refField.setAccessible(true);
      Object value = refField.get(myObject);
      return value == null ? null : MockValue.createValue(value, refField.getType(), myVirtualMachine);
    }
    catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Map<Field, Value> getValues(List<? extends Field> list) {
    throw new UnsupportedOperationException("Not implemented: \"getValues\" in " + getClass().getName());
  }

  @Override
  public void setValue(Field field,Value value) {
    throw new UnsupportedOperationException("Not implemented: \"setValue\" in " + getClass().getName());
  }

  @Override
  public Value invokeMethod(ThreadReference threadReference,Method method,List<? extends Value> list,int i) {
    Object[] argsArray = MockClassType.values2Objects(list);
    try {
      java.lang.reflect.Method refMethod = ((MockMethod) method).getMethod();
      refMethod.setAccessible(true);
      return MockValue.createValue(refMethod.invoke(myObject, argsArray), refMethod.getReturnType(), myVirtualMachine);
    }
    catch (IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void disableCollection() {
  }

  @Override
  public void enableCollection() {
  }

  @Override
  public boolean isCollected() {
    return false;
  }

  @Override
  public long uniqueID() {
    throw new UnsupportedOperationException("Not implemented: \"uniqueID\" in " + getClass().getName());
  }

  @Override
  public List<ThreadReference> waitingThreads() {
    throw new UnsupportedOperationException("Not implemented: \"waitingThreads\" in " + getClass().getName());
  }

  @Override
  public ThreadReference owningThread() {
    throw new UnsupportedOperationException("Not implemented: \"owningThread\" in " + getClass().getName());
  }

  @Override
  public int entryCount() {
    throw new UnsupportedOperationException("Not implemented: \"entryCount\" in " + getClass().getName());
  }

  @Override
  public List<ObjectReference> referringObjects(long l) {
    throw new UnsupportedOperationException("Not implemented: \"referringObjects\" in " + getClass().getName());
  }

  @Override
  public Object getValue() {
    return myObject;
  }

  public static MockObjectReference createObjectReference(Object o, Class<?> type, final MockVirtualMachine virtualMachine) {
    if (o instanceof String || type != null && String.class.isAssignableFrom(type)) {
      return new MockStringReference(virtualMachine, (String) o);
    }
    if (o instanceof Thread || type != null && Thread.class.isAssignableFrom(type)) {
      return new MockThreadReference(virtualMachine, (Thread) o);
    }
    if (o instanceof ClassLoader || type != null && ClassLoader.class.isAssignableFrom(type)) {
      return new MockClassLoaderReference(virtualMachine, (ClassLoader) o);
    }
    if (o instanceof Object[] || type != null && type.isArray()) {
      return new MockArrayReference(virtualMachine, o, type == null ? o.getClass() : type);
    }
    return new MockObjectReference(virtualMachine, o);
  }
}
