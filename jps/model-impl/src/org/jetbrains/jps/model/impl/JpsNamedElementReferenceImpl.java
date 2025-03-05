// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsCompositeElement;
import org.jetbrains.jps.model.JpsElementCollection;
import org.jetbrains.jps.model.JpsElementReference;
import org.jetbrains.jps.model.JpsNamedElement;
import org.jetbrains.jps.model.ex.JpsElementCollectionRole;

/**
 * This class is for internal use only, override {@link JpsNamedElementReferenceBase} instead.
 */
@ApiStatus.Internal
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

  @Override
  protected @Nullable JpsElementCollection<? extends T> getCollection(@NotNull JpsCompositeElement parent) {
    return parent.getContainer().getChild(myCollectionRole);
  }
}
