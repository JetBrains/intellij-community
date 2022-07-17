/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.mockJDI.values;

import com.sun.jdi.StringReference;
import com.intellij.debugger.mockJDI.MockVirtualMachine;

public class MockStringReference extends MockObjectReference implements StringReference {
  public MockStringReference(final MockVirtualMachine virtualMachine, String object) {
    super(virtualMachine, object);
  }

  @Override
  public String value() {
    return (String) getValue();
  }
}
