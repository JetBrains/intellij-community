package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsCompositeElement;
import org.jetbrains.jps.model.JpsElementReference;
import org.jetbrains.jps.model.JpsNamedElement;

/**
 * @author nik
 */
public abstract class JpsNamedElementReferenceImpl<T extends JpsNamedElement, Self extends JpsNamedElementReferenceImpl<T, Self>> extends JpsNamedElementReferenceBase<T, T, Self> {
  protected JpsNamedElementReferenceImpl(@NotNull JpsElementCollectionRole<? extends T> role, @NotNull String elementName,
                                         @NotNull JpsElementReference<? extends JpsCompositeElement> parentReference) {
    super(role, elementName, parentReference);
  }

  protected JpsNamedElementReferenceImpl(JpsNamedElementReferenceImpl<T, Self> original) {
    super(original);
  }

  @Override
  protected T resolve(T element) {
    return element;
  }
}
