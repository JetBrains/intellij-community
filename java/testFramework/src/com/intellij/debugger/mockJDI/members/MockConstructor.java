/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.mockJDI.members;

import com.intellij.debugger.mockJDI.MockVirtualMachine;

import java.lang.reflect.Constructor;

public class MockConstructor extends AbstractMockMethod {
  public MockConstructor(Constructor constructor, final MockVirtualMachine virtualMachine) {
    super(constructor, virtualMachine);
  }

  public Constructor getConstructor() {
    return (Constructor) myMember;
  }

  @Override
  public String name() {
    return "<init>";
  }

  @Override
  protected Class<?> getReturnType() {
    return void.class;
  }

  @Override
  protected Class<?>[] getParameterTypes() {
    return getConstructor().getParameterTypes();
  }
}
