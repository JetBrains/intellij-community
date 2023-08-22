// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.mockJDI.values;

import com.intellij.debugger.mockJDI.MockVirtualMachine;
import com.sun.jdi.StringReference;

public class MockStringReference extends MockObjectReference implements StringReference {
  public MockStringReference(final MockVirtualMachine virtualMachine, String object) {
    super(virtualMachine, object);
  }

  @Override
  public String value() {
    return (String)getValue();
  }
}
