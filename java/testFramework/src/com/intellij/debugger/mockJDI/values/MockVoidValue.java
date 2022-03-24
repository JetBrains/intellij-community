/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.mockJDI.values;

import com.sun.jdi.VoidValue;
import com.intellij.debugger.mockJDI.MockVirtualMachine;

public class MockVoidValue extends MockValue implements VoidValue {

  public MockVoidValue(final MockVirtualMachine virtualMachine) {
    super(virtualMachine);
  }

  @Override
  public Object getValue() {
    throw new UnsupportedOperationException("Not implemented: \"getValue\" in " + getClass().getName());
  }
}
