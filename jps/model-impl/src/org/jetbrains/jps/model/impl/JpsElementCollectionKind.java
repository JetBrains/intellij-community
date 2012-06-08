package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;

/**
 * @author nik
 */
public class JpsElementCollectionKind<E extends JpsElement> extends JpsElementKind<JpsElementCollectionImpl<E>>
                                                            implements JpsElementCreator<JpsElementCollectionImpl<E>> {
  private final JpsElementKind<E> myElementKind;

  public JpsElementCollectionKind(JpsElementKind<E> elementKind) {
    myElementKind = elementKind;
  }

  @NotNull
  @Override
  public JpsElementCollectionImpl<E> create() {
    return new JpsElementCollectionImpl<E>(myElementKind);
  }
}
