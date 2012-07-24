package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;

/**
 * @author nik
 */
public class JpsElementCollectionKind<E extends JpsElement> extends JpsElementKindBase<JpsElementCollectionImpl<E>>
                                                            implements JpsElementCreator<JpsElementCollectionImpl<E>> {
  private final JpsElementKind<E> myElementKind;

  public JpsElementCollectionKind(@NotNull JpsElementKind<E> elementKind) {
    super("collection of " + elementKind);
    myElementKind = elementKind;
  }

  @NotNull
  @Override
  public JpsElementCollectionImpl<E> create() {
    return new JpsElementCollectionImpl<E>(myElementKind);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    return myElementKind.equals(((JpsElementCollectionKind)o).myElementKind);
  }

  @Override
  public int hashCode() {
    return myElementKind.hashCode();
  }
}
