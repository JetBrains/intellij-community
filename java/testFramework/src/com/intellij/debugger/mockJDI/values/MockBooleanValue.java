// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.mockJDI.values;

import com.intellij.debugger.mockJDI.MockVirtualMachine;
import com.intellij.debugger.mockJDI.types.MockType;
import com.sun.jdi.BooleanValue;
import com.sun.jdi.Type;

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
