package org.jetbrains.jps.model.library.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsEventDispatcher;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.library.JpsLibraryRoot;
import org.jetbrains.jps.model.library.JpsLibraryRootListener;
import org.jetbrains.jps.model.library.JpsOrderRootType;

/**
 * @author nik
 */
public class JpsLibraryRootRole extends JpsElementChildRoleBase<JpsLibraryRoot> {
  private final JpsOrderRootType myRootType;

  public JpsLibraryRootRole(@NotNull JpsOrderRootType rootType) {
    super("library root");
    myRootType = rootType;
  }

  @Override
  public void fireElementAdded(@NotNull JpsEventDispatcher dispatcher, @NotNull JpsLibraryRoot element) {
    dispatcher.getPublisher(JpsLibraryRootListener.class).rootAdded(element);
  }

  @Override
  public void fireElementRemoved(@NotNull JpsEventDispatcher dispatcher, @NotNull JpsLibraryRoot element) {
    dispatcher.getPublisher(JpsLibraryRootListener.class).rootRemoved(element);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    return myRootType.equals(((JpsLibraryRootRole)o).myRootType);
  }

  @Override
  public int hashCode() {
    return myRootType.hashCode();
  }
}
