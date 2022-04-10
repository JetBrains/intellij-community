/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.mockJDI.values;

import com.sun.jdi.BooleanValue;
import com.sun.jdi.Type;
import com.intellij.debugger.mockJDI.types.MockType;
import com.intellij.debugger.mockJDI.MockVirtualMachine;

public class MockBooleanValue extends MockPrimitiveValue implements BooleanValue {
  private final boolean myValue;

  public MockBooleanValue(final MockVirtualMachine virtualMachine, boolean value) {
    super(virtualMachine);
    myValue = value;
  }

  @Override
  public boolean booleanValue() {
    return myValue;
  }

  @Override
  public Object getValue() {
    return value();
  }

  @Override
  public Type type() {
    return MockType.createType(myVirtualMachine, boolean.class);
  }

  @Override
  public boolean value() {
    return myValue;
  }
}
