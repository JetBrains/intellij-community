// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.mockJDI;

import com.sun.jdi.Mirror;

public class MockMirror implements Mirror {
  protected MockVirtualMachine myVirtualMachine;

  public MockMirror(final MockVirtualMachine virtualMachine) {
    myVirtualMachine = virtualMachine;
  }

  @Override
  public MockVirtualMachine virtualMachine() {
    return myVirtualMachine;
  }
}
