/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.mockJDI;

import com.sun.jdi.Mirror;
import com.sun.jdi.VirtualMachine;

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
