/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.mockJDI.types;

import com.intellij.debugger.mockJDI.MockVirtualMachine;
import com.intellij.debugger.mockJDI.members.MockConstructor;
import com.intellij.debugger.mockJDI.members.MockMethod;
import com.intellij.debugger.mockJDI.values.MockObjectReference;
import com.intellij.debugger.mockJDI.values.MockValue;
import com.sun.jdi.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

public class MockClassType extends MockReferenceType implements ClassType {

  public MockClassType(final MockVirtualMachine virtualMachine, Class type) {
    super(type, virtualMachine);
  }

  @Override
  protected List<ReferenceType> getThisAndAllSupers() {
    ArrayList<ReferenceType> referenceTypes = new ArrayList<>();
    ClassType type = this;
    do {
      referenceTypes.add(type);
      type = type.superclass();
    }
    while (type != null);
    referenceTypes.addAll(allInterfaces());
    return referenceTypes;
  }

  @Override
  public ClassType superclass() {
    Class superclass = myType.getSuperclass();
    return superclass != null ? (MockClassType)myVirtualMachine.createReferenceType(superclass) : null;
  }

  @Override
  public List<InterfaceType> interfaces() {
    ArrayList<InterfaceType> interfaceTypes = new ArrayList<>();
    for (Class aClass : myType.getInterfaces()) {
      interfaceTypes.add(myVirtualMachine.createInterfaceType(aClass));
    }
    return interfaceTypes;
  }

  @Override
  public List<ClassType> subclasses() {
    throw new UnsupportedOperationException("Not implemented: \"subclasses\" in " + getClass().getName());
  }

  @Override
  public boolean isEnum() {
    return myType.isEnum();
  }

  @Override
  public void setValue(Field field,Value value) {
    throw new UnsupportedOperationException("Not implemented: \"setValue\" in " + getClass().getName());
  }

  @Override
  public Value invokeMethod(ThreadReference threadReference,Method method,List<? extends Value> list,int i) throws InvocationException {
    try {
      Object[] args = values2Objects(list);
      java.lang.reflect.Method refMethod = ((MockMethod) method).getMethod();
      return MockValue.createValue(refMethod.invoke(null, args), refMethod.getReturnType(), myVirtualMachine);
    }
    catch (IllegalAccessException | InvocationTargetException e) {
      throw new InvocationException(null);
    }
  }

  public static Object[] values2Objects(List<? extends Value> list) {
    return list.stream().map(value -> ((MockValue)value).getValue()).toArray();
  }

  @Override
  public ObjectReference newInstance(ThreadReference threadReference,Method method,List<? extends Value> list,int i) throws
                                                                                                                     InvocationException {
    Object[] args = values2Objects(list);
    Constructor constructor = ((MockConstructor) method).getConstructor();
    try {
      return MockObjectReference.createObjectReference(constructor.newInstance(args), myType, myVirtualMachine);
    }
    catch (InstantiationException | InvocationTargetException | IllegalAccessException e) {
      throw new InvocationException(null);
    }
  }

  @Override
  public Method concreteMethodByName(String string,String string1) {
    List<Method> methods = methodsByName(string, string1);
    if (methods.isEmpty()) {
      return null;
    }
    return methods.get(0);
  }

  @Override
  public List<ObjectReference> instances(long l) {
    throw new UnsupportedOperationException("Not implemented: \"instances\" in " + getClass().getName());
  }

  @Override
  public int majorVersion() {
    throw new UnsupportedOperationException("Not implemented: \"majorVersion\" in " + getClass().getName());
  }

  @Override
  public int minorVersion() {
    throw new UnsupportedOperationException("Not implemented: \"minorVersion\" in " + getClass().getName());
  }

  @Override
  public int constantPoolCount() {
    throw new UnsupportedOperationException("Not implemented: \"constantPoolCount\" in " + getClass().getName());
  }

  @Override
  public byte[] constantPool() {
    throw new UnsupportedOperationException("Not implemented: \"constantPool\" in " + getClass().getName());
  }
}
