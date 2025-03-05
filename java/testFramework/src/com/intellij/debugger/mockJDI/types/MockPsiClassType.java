// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.mockJDI.types;

import com.intellij.debugger.mockJDI.MockVirtualMachine;
import com.intellij.psi.PsiClass;
import com.intellij.util.containers.ContainerUtil;
import com.sun.jdi.*;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

public class MockPsiClassType extends MockPsiReferenceType implements ClassType {
  public MockPsiClassType(MockVirtualMachine virtualMachine, PsiClass psiClass) {
    super(virtualMachine, psiClass);
  }

  @Override
  public @Nullable ClassType superclass() {
    return (ClassType)myVirtualMachine.createReferenceType(myClass.getSuperClass());
  }

  @Override
  public @Unmodifiable List<InterfaceType> interfaces() {
    return ContainerUtil.map(myClass.getInterfaces(), iFace -> (InterfaceType)myVirtualMachine.createReferenceType(iFace));
  }

  @Override
  public @Unmodifiable List<InterfaceType> allInterfaces() {
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
