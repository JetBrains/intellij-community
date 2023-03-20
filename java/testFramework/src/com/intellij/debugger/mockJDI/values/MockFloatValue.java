/*
 * Copyright (c) 2000-2019 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.mockJDI.values;

import com.intellij.debugger.mockJDI.MockVirtualMachine;
import com.intellij.debugger.mockJDI.types.MockType;
import com.sun.jdi.FloatValue;
import com.sun.jdi.Type;

public class MockFloatValue extends MockPrimitiveValue implements FloatValue {
  private final float myValue;

  public MockFloatValue(final MockVirtualMachine virtualMachine, float value) {
    super(virtualMachine);
    myValue = value;
  }

  @Override
  public Object getValue() {
    return value();
  }

  @Override
  public float floatValue() {
    return myValue;
  }

  @Override
  public Type type() {
    return MockType.createType(myVirtualMachine, float.class);
  }

  @Override
  public float value() {
    return myValue;
  }

  @Override
  public int compareTo(FloatValue o) {
    return Float.compare(value(), o.value());
  }
}
