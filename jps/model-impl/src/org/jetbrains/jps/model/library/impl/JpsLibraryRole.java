// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.library.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsEventDispatcher;
import org.jetbrains.jps.model.ex.JpsElementCollectionRole;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryListener;

public final class JpsLibraryRole extends JpsElementChildRoleBase<JpsLibrary> {
  private static final JpsLibraryRole INSTANCE = new JpsLibraryRole();
  public static final JpsElementCollectionRole<JpsLibrary> LIBRARIES_COLLECTION_ROLE = JpsElementCollectionRole.create(INSTANCE);

  private JpsLibraryRole() {
    super("library");
  }

  @Override
  public void fireElementAdded(@NotNull JpsEventDispatcher dispatcher, @NotNull JpsLibrary element) {
    dispatcher.getPublisher(JpsLibraryListener.class).libraryAdded(element);
  }

  @Override
  public void fireElementRemoved(@NotNull JpsEventDispatcher dispatcher, @NotNull JpsLibrary element) {
    dispatcher.getPublisher(JpsLibraryListener.class).libraryRemoved(element);
  }
}
