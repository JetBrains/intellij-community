/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.mockJDI.members;

import com.intellij.debugger.mockJDI.MockVirtualMachine;

public class MockMethod extends AbstractMockMethod {
  public MockMethod(java.lang.reflect.Method member, final MockVirtualMachine virtualMachine) {
    super(member, virtualMachine);
  }

  public java.lang.reflect.Method getMethod() {
    return (java.lang.reflect.Method) myMember;
  }

  @Override
  protected Class<?> getReturnType() {
    return getMethod().getReturnType();
  }

  @Override
  protected Class<?>[] getParameterTypes() {
    return getMethod().getParameterTypes();
  }

}
