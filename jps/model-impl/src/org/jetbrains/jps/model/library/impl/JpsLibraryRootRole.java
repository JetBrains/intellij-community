// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.library.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.library.JpsLibraryRoot;
import org.jetbrains.jps.model.library.JpsOrderRootType;

final class JpsLibraryRootRole extends JpsElementChildRoleBase<JpsLibraryRoot> {
  private final JpsOrderRootType rootType;

  JpsLibraryRootRole(@NotNull JpsOrderRootType rootType) {
    super("library root");
    this.rootType = rootType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    return rootType.equals(((JpsLibraryRootRole)o).rootType);
  }

  @Override
  public int hashCode() {
    return rootType.hashCode();
  }
}
