/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.mockJDI.types;

import com.sun.jdi.LongType;
import com.intellij.debugger.mockJDI.MockVirtualMachine;

public class MockLongType extends MockPrimitiveType implements LongType {
  public MockLongType(final MockVirtualMachine virtualMachine) {
    super(virtualMachine);
  }

  @Override
  public String name() {
    return "long";
  }
}
