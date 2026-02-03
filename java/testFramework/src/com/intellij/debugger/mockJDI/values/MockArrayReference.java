/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.debugger.mockJDI.values;

import com.intellij.debugger.mockJDI.MockVirtualMachine;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.Value;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

public class MockArrayReference extends MockObjectReference implements ArrayReference {
  private final Class<?> myType;

  public MockArrayReference(final MockVirtualMachine virtualMachine, final Object o, final Class<?> type) {
    super(virtualMachine, o);
    myType = type;
  }

  @Override
  public int length() {
    return Array.getLength(getValue());
  }

  @Override
  public Value getValue(int i) {
    return MockValue.createValue(Array.get(getValue(), i), myType.getComponentType(), myVirtualMachine);
  }

  @Override
  public List<Value> getValues() {
    return getValues(0, length());
  }

  @Override
  public List<Value> getValues(int from, int to) {
    final ArrayList<Value> list = new ArrayList<>();
    for (int i = from; i < to; i++) {
      list.add(getValue(i));
    }
    return list;
  }

  @Override
  public void setValue(int i, Value value) {
    throw new UnsupportedOperationException("'setValue' not implemented in " + getClass().getName());
  }

  @Override
  public void setValues(List<? extends Value> list) {
    throw new UnsupportedOperationException("'setValues' not implemented in " + getClass().getName());
  }

  @Override
  public void setValues(int i, List<? extends Value> list, int i1, int i2) {
    throw new UnsupportedOperationException("'setValues' not implemented in " + getClass().getName());
  }
}
