/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.mockJDI.values;

import com.sun.jdi.PrimitiveValue;
import com.intellij.debugger.mockJDI.MockVirtualMachine;

import java.util.Objects;

public abstract class MockPrimitiveValue extends MockValue implements PrimitiveValue{
  protected MockPrimitiveValue(final MockVirtualMachine virtualMachine) {
    super(virtualMachine);
  }

  @Override
  public boolean booleanValue() {
    throw new UnsupportedOperationException("Not implemented: \"booleanValue\" in " + getClass().getName());
  }

  @Override
  public byte byteValue() {
    throw new UnsupportedOperationException("Not implemented: \"byteValue\" in " + getClass().getName());
  }

  @Override
  public char charValue() {
    throw new UnsupportedOperationException("Not implemented: \"charValue\" in " + getClass().getName());
  }

  @Override
  public short shortValue() {
    throw new UnsupportedOperationException("Not implemented: \"shortValue\" in " + getClass().getName());
  }

  @Override
  public int intValue() {
    throw new UnsupportedOperationException("Not implemented: \"intValue\" in " + getClass().getName());
  }

  @Override
  public long longValue() {
    throw new UnsupportedOperationException("Not implemented: \"longValue\" in " + getClass().getName());
  }

  @Override
  public float floatValue() {
    throw new UnsupportedOperationException("Not implemented: \"floatValue\" in " + getClass().getName());
  }

  @Override
  public double doubleValue() {
    throw new UnsupportedOperationException("Not implemented: \"doubleValue\" in " + getClass().getName());
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof MockPrimitiveValue && Objects.equals(getValue(), ((MockPrimitiveValue)obj).getValue());
  }

  @Override
  public String toString() {
    return String.valueOf(getValue());
  }
}
