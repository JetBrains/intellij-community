// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.ex;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.JpsElementCollection;
import org.jetbrains.jps.model.JpsElementCreator;

public final class JpsElementCollectionRole<E extends JpsElement> extends JpsElementChildRoleBase<JpsElementCollection<E>>
                                                            implements JpsElementCreator<JpsElementCollection<E>> {
  private final JpsElementChildRole<E> myChildRole;

  private JpsElementCollectionRole(@NotNull JpsElementChildRole<E> role) {
    super("collection of " + role);
    myChildRole = role;
  }

  @Override
  public @NotNull JpsElementCollection<E> create() {
    return JpsExElementFactory.getInstance().createCollection(myChildRole);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    return myChildRole.equals(((JpsElementCollectionRole)o).myChildRole);
  }

  @Override
  public int hashCode() {
    return myChildRole.hashCode();
  }

  public static <E extends JpsElement> JpsElementCollectionRole<E> create(@NotNull JpsElementChildRole<E> role) {
    return new JpsElementCollectionRole<>(role);
  }
}
