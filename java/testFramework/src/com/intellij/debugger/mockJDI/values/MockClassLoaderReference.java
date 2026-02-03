// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.mockJDI.values;

import com.intellij.debugger.mockJDI.MockVirtualMachine;
import com.sun.jdi.ClassLoaderReference;
import com.sun.jdi.ReferenceType;

import java.util.List;

public class MockClassLoaderReference extends MockObjectReference implements ClassLoaderReference {
  public MockClassLoaderReference(final MockVirtualMachine virtualMachine, ClassLoader loader) {
    super(virtualMachine, loader);
  }

  public ClassLoader getLoader() {
    return (ClassLoader)getValue();
  }

  @Override
  public List<ReferenceType> definedClasses() {
    throw new UnsupportedOperationException("Not implemented: \"definedClasses\" in " + getClass().getName());
  }

  @Override
  public List<ReferenceType> visibleClasses() {
    throw new UnsupportedOperationException("Not implemented: \"visibleClasses\" in " + getClass().getName());
  }

  @Override
  public boolean equals(Object obj) {
    // For now assume that all mocked class loaders are the same
    return obj instanceof MockClassLoaderReference;
  }
}
