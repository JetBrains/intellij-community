/*
 * Copyright (c) 2000-2019 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.mockJDI.values;

import com.intellij.debugger.mockJDI.MockMirror;
import com.intellij.debugger.mockJDI.MockVirtualMachine;
import com.sun.jdi.Type;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;

public abstract class MockValue extends MockMirror implements Value {
  public MockValue(final MockVirtualMachine virtualMachine) {
    super(virtualMachine);
  }

  public abstract Object getValue();

  @Override
  public Type type() {
    throw new UnsupportedOperationException("Not implemented: \"type\" in " + getClass().getName());
  }

  public static MockValue createValue(@NotNull Object o, final MockVirtualMachine virtualMachine) {
    return MockObjectReference.createObjectReference(o, o.getClass(), virtualMachine);
  }

  public static MockValue createValue(Object o, Class<?> type, final MockVirtualMachine virtualMachine) {
    if (type == boolean.class) {
      return new MockBooleanValue(virtualMachine, (Boolean)o);
    }
    if (type == int.class) {
      return new MockIntegerValue(virtualMachine, (Integer)o);
    }
    if (type == float.class) {
      return new MockFloatValue(virtualMachine, (Float)o);
    }
    if (type == double.class) {
      return new MockDoubleValue(virtualMachine, (Double)o);
    }
    return MockObjectReference.createObjectReference(o, type, virtualMachine);
  }

}
