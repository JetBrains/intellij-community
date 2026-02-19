// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.mockJDI.values;

import com.intellij.debugger.mockJDI.MockVirtualMachine;
import com.sun.jdi.VoidValue;

public class MockVoidValue extends MockValue implements VoidValue {

  public MockVoidValue(final MockVirtualMachine virtualMachine) {
    super(virtualMachine);
  }

  @Override
  public Object getValue() {
    throw new UnsupportedOperationException("Not implemented: \"getValue\" in " + getClass().getName());
  }
}
