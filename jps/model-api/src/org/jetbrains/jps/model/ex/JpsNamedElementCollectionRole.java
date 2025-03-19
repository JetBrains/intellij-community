// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.ex;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;

public final class JpsNamedElementCollectionRole<E extends JpsNamedElement> extends JpsElementChildRoleBase<JpsNamedElementCollection<E>>
                                                            implements JpsElementCreator<JpsNamedElementCollection<E>> {
  private final JpsElementChildRole<E> myChildRole;

  private JpsNamedElementCollectionRole(@NotNull JpsElementChildRole<E> role) {
    super("collection of " + role);
    myChildRole = role;
  }

  @Override
  public @NotNull JpsNamedElementCollection<E> create() {
    return JpsExElementFactory.getInstance().createNamedElementCollection(myChildRole);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    return myChildRole.equals(((JpsNamedElementCollectionRole)o).myChildRole);
  }

  @Override
  public int hashCode() {
    return myChildRole.hashCode();
  }

  public static <E extends JpsNamedElement> JpsNamedElementCollectionRole<E> create(@NotNull JpsElementChildRole<E> role) {
    return new JpsNamedElementCollectionRole<>(role);
  }
}
