/*
 * Copyright (c) 2000-2019 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.mockJDI.types;

import com.intellij.debugger.mockJDI.MockVirtualMachine;
import com.intellij.psi.PsiClass;
import com.sun.jdi.*;
import one.util.streamex.StreamEx;

import java.util.List;

public class MockPsiInterfaceType extends MockPsiReferenceType implements InterfaceType {
  public MockPsiInterfaceType(MockVirtualMachine virtualMachine, PsiClass psiClass) {
    super(virtualMachine, psiClass);
  }

  @Override
  public List<InterfaceType> superinterfaces() {
    return StreamEx.of(myClass.getInterfaces())
      .map(iFace -> (InterfaceType)myVirtualMachine.createReferenceType(iFace))
      .toList();
  }

  @Override
  public List<InterfaceType> subinterfaces() {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<ClassType> implementors() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Value invokeMethod(ThreadReference reference, Method method, List<? extends Value> list, int i) {
    throw new UnsupportedOperationException();
  }
}
