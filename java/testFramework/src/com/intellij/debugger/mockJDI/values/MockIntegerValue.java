/*
 * Copyright (c) 2000-2019 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.mockJDI.values;

import com.intellij.debugger.mockJDI.MockVirtualMachine;
import com.intellij.debugger.mockJDI.types.MockType;
import com.sun.jdi.IntegerValue;
import com.sun.jdi.Type;

public class MockIntegerValue extends MockPrimitiveValue implements IntegerValue {
  private final int myValue;

  public MockIntegerValue(final MockVirtualMachine virtualMachine, int value) {
    super(virtualMachine);
    myValue = value;
  }

  @Override
  public Object getValue() {
    return value();
  }

  @Override
  public int intValue() {
    return myValue;
  }

  @Override
  public long longValue() {
    return myValue;
  }

  @Override
  public Type type() {
    return MockType.createType(myVirtualMachine, int.class);
  }

  @Override
  public int value() {
    return myValue;
  }

  @Override
  public int compareTo(IntegerValue o) {
    return Integer.compare(value(), o.value());
  }
}
