package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsCompositeElement;
import org.jetbrains.jps.model.JpsElementReference;
import org.jetbrains.jps.model.JpsNamedElement;

/**
 * @author nik
 */
public abstract class JpsNamedElementReferenceImpl<T extends JpsNamedElement, Self extends JpsNamedElementReferenceImpl<T, Self>> extends JpsNamedElementReferenceBase<T, T, Self> {
  protected final JpsElementCollectionRole<? extends T> myCollectionRole;

  protected JpsNamedElementReferenceImpl(@NotNull JpsElementCollectionRole<? extends T> role, @NotNull String elementName,
                                         @NotNull JpsElementReference<? extends JpsCompositeElement> parentReference) {
    super(elementName, parentReference);
    myCollectionRole = role;
  }

  protected JpsNamedElementReferenceImpl(JpsNamedElementReferenceImpl<T, Self> original) {
    super(original);
    myCollectionRole = original.myCollectionRole;
  }

  @Override
  protected T resolve(T element) {
    return element;
  }

  @Nullable
  protected JpsElementCollectionImpl<? extends T> getCollection(@NotNull JpsCompositeElement parent) {
    return parent.getContainer().getChild(myCollectionRole);
  }
}
