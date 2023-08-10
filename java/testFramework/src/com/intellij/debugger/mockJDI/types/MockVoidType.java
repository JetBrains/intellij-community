// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.mockJDI.types;

import com.intellij.debugger.mockJDI.MockVirtualMachine;
import com.sun.jdi.VoidType;

public class MockVoidType extends MockPrimitiveType implements VoidType {
  public MockVoidType(final MockVirtualMachine virtualMachine) {
    super(virtualMachine);
  }

  @Override
  public String name() {
    return "void";
  }
}
