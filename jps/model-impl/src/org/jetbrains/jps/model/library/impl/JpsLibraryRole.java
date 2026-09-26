// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.library.impl;

import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.ex.JpsNamedElementCollectionRole;
import org.jetbrains.jps.model.library.JpsLibrary;

public final class JpsLibraryRole extends JpsElementChildRoleBase<JpsLibrary> {
  private static final JpsLibraryRole INSTANCE = new JpsLibraryRole();
  public static final JpsNamedElementCollectionRole<JpsLibrary> LIBRARIES_COLLECTION_ROLE = JpsNamedElementCollectionRole.create(INSTANCE);

  private JpsLibraryRole() {
    super("library");
  }
}
