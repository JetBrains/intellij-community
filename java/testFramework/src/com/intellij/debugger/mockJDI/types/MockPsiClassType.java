/*
 * Copyright (c) 2000-2019 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.mockJDI.types;

import com.intellij.debugger.mockJDI.MockVirtualMachine;
import com.intellij.psi.PsiClass;
import com.intellij.util.containers.ContainerUtil;
import com.sun.jdi.*;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MockPsiClassType extends MockPsiReferenceType implements ClassType {
  public MockPsiClassType(MockVirtualMachine virtualMachine, PsiClass psiClass) {
    super(virtualMachine, psiClass);
  }

  @Nullable
  @Override
  public ClassType superclass() {
    return (ClassType)myVirtualMachine.createReferenceType(myClass.getSuperClass());
  }

  @Override
  public List<InterfaceType> interfaces() {
    return ContainerUtil.map(myClass.getInterfaces(), iFace -> (InterfaceType)myVirtualMachine.createReferenceType(iFace));
  }

  @Override
  public List<InterfaceType> allInterfaces() {
    return interfaces();
  }

  @Override
  public List<ClassType> subclasses() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isEnum() {
    return myClass.isEnum();
  }

  @Override
  public void setValue(Field field, Value value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Value invokeMethod(ThreadReference reference, Method method, List<? extends Value> list, int i) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ObjectReference newInstance(ThreadReference reference, Method method, List<? extends Value> list, int i) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Method concreteMethodByName(String s, String s1) {
    throw new UnsupportedOperationException();
  }
}
