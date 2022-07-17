/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.mockJDI.values;

import com.sun.jdi.ClassLoaderReference;
import com.sun.jdi.ReferenceType;
import com.intellij.debugger.mockJDI.MockVirtualMachine;

import java.util.List;

public class MockClassLoaderReference extends MockObjectReference implements ClassLoaderReference {
  public MockClassLoaderReference(final MockVirtualMachine virtualMachine, ClassLoader loader) {
    super(virtualMachine, loader);
  }

  public ClassLoader getLoader() {
    return (ClassLoader) getValue();
  }

  @Override
  public List<ReferenceType> definedClasses() {
    throw new UnsupportedOperationException("Not implemented: \"definedClasses\" in " + getClass().getName());
  }

  @Override
  public List<ReferenceType> visibleClasses() {
    throw new UnsupportedOperationException("Not implemented: \"visibleClasses\" in " + getClass().getName());
  }

  public boolean equals(Object obj) {
    // For now assume that all mocked class loaders are the same
    return obj instanceof MockClassLoaderReference;
  }
}
