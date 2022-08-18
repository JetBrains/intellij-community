/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.mockJDI.types;

import com.sun.jdi.BooleanType;
import com.intellij.debugger.mockJDI.MockVirtualMachine;

public class MockBooleanType extends MockPrimitiveType implements BooleanType {
  public MockBooleanType(final MockVirtualMachine virtualMachine) {
    super(virtualMachine);
  }

  @Override
  public String name() {
    return "boolean";
  }
}
