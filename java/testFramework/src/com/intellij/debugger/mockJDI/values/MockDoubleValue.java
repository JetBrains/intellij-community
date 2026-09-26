/*
 * Copyright (c) 2000-2019 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.mockJDI.values;

import com.intellij.debugger.mockJDI.MockVirtualMachine;
import com.intellij.debugger.mockJDI.types.MockType;
import com.sun.jdi.DoubleValue;
import com.sun.jdi.Type;

public class MockDoubleValue extends MockPrimitiveValue implements DoubleValue {
  private final double myValue;

  public MockDoubleValue(final MockVirtualMachine virtualMachine, double value) {
    super(virtualMachine);
    myValue = value;
  }

  @Override
  public Object getValue() {
    return value();
  }

  @Override
  public double doubleValue() {
    return myValue;
  }

  @Override
  public Type type() {
    return MockType.createType(myVirtualMachine, double.class);
  }

  @Override
  public double value() {
    return myValue;
  }

  @Override
  public int compareTo(DoubleValue o) {
    return Double.compare(value(), o.value());
  }
}
