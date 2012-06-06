package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;

/**
 * @author nik
 */
public class JpsElementCollectionKind<E extends JpsElementBase<?>> extends JpsElementKind<JpsElementCollectionImpl<E>>
  implements JpsElementFactory<JpsElementCollectionImpl<E>> {
  private final JpsElementKind<E> myElementKind;

  public JpsElementCollectionKind(JpsElementKind<E> elementKind) {
    myElementKind = elementKind;
  }

  @NotNull
  @Override
  public JpsElementCollectionImpl<E> create(@NotNull JpsModel model,
                                            @NotNull JpsEventDispatcher eventDispatcher,
                                            JpsParentElement parent) {
    return new JpsElementCollectionImpl<E>(myElementKind, model, eventDispatcher, parent);
  }
}
