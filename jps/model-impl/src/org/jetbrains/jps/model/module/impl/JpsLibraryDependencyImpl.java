// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.module.impl;

import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryReference;
import org.jetbrains.jps.model.module.JpsLibraryDependency;

final class JpsLibraryDependencyImpl extends JpsDependencyElementBase<JpsLibraryDependencyImpl> implements JpsLibraryDependency {
  public static final JpsElementChildRole<JpsLibraryReference>
    LIBRARY_REFERENCE_CHILD_ROLE = JpsElementChildRoleBase.create("library reference");

  private volatile Ref<JpsLibrary> myCachedLibrary = null;
  
  JpsLibraryDependencyImpl(final JpsLibraryReference reference) {
    super();
    myContainer.setChild(LIBRARY_REFERENCE_CHILD_ROLE, reference);
  }

  JpsLibraryDependencyImpl(JpsLibraryDependencyImpl original) {
    super(original);
  }

  @Override
  public @NotNull JpsLibraryReference getLibraryReference() {
    return myContainer.getChild(LIBRARY_REFERENCE_CHILD_ROLE);
  }

  @Override
  public JpsLibrary getLibrary() {
    Ref<JpsLibrary> libRef = myCachedLibrary;
    if (libRef == null) {
      libRef = new Ref<>(getLibraryReference().resolve());
      myCachedLibrary = libRef;
    }
    return libRef.get();
  }

  @Override
  public @NotNull JpsLibraryDependencyImpl createCopy() {
    return new JpsLibraryDependencyImpl(this);
  }

  @Override
  public String toString() {
    return "lib dep [" + getLibraryReference() + "]";
  }
}
