/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.mockJDI.types;

import com.intellij.debugger.engine.JVMNameUtil;
import com.intellij.debugger.mockJDI.MockVirtualMachine;

public class MockPrimitiveType extends MockType {
  public MockPrimitiveType(final MockVirtualMachine virtualMachine) {
    super(virtualMachine);
  }

  @Override
  public String signature() {
    return JVMNameUtil.getPrimitiveSignature(name());
  }
}
