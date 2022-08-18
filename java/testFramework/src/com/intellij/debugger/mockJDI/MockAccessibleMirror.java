/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.mockJDI;

import com.sun.jdi.Accessible;

public class MockAccessibleMirror extends MockMirror implements Accessible {
  public MockAccessibleMirror(final MockVirtualMachine virtualMachine) {
    super(virtualMachine);
  }

  @Override
  public int modifiers() {
    return 0;
  }

  @Override
  public boolean isPrivate() {
    return false;
  }

  @Override
  public boolean isPackagePrivate() {
    return false;
  }

  @Override
  public boolean isProtected() {
    return false;
  }

  @Override
  public boolean isPublic() {
    return true;
  }
}
