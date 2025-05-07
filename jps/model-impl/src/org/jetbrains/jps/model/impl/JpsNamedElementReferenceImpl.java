// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.ex.JpsElementCollectionRole;
import org.jetbrains.jps.model.ex.JpsNamedElementCollectionRole;

/**
 * This class is for internal use only, override {@link JpsNamedElementReferenceBase} instead.
 */
@ApiStatus.Internal
public abstract class JpsNamedElementReferenceImpl<T extends JpsNamedElement, Self extends JpsNamedElementReferenceImpl<T, Self>> extends JpsNamedElementReferenceBase<T, T, Self> {
  /**
   * @deprecated use {@link #getCollectionRole()} instead
   */
  @SuppressWarnings("DeprecatedIsStillUsed") 
  @Deprecated
  protected final @Nullable JpsElementCollectionRole<? extends T> myCollectionRole;
  private final JpsElementChildRoleBase<? extends JpsElementCollection<? extends T>> myActualCollectionRole;

  /**
   * @deprecated use {@link #JpsNamedElementReferenceImpl(JpsNamedElementCollectionRole, String, JpsElementReference)} instead JpsNamedElementReferenceImpl}
   */
  @Deprecated
  protected JpsNamedElementReferenceImpl(@NotNull JpsElementCollectionRole<? extends T> role, @NotNull String elementName,
                                         @NotNull JpsElementReference<? extends JpsCompositeElement> parentReference) {
    super(elementName, parentReference);
    myCollectionRole = role;
    myActualCollectionRole = role;
  }

  protected JpsNamedElementReferenceImpl(@NotNull JpsNamedElementCollectionRole<? extends T> role, @NotNull String elementName,
                                         @NotNull JpsElementReference<? extends JpsCompositeElement> parentReference) {
    super(elementName, parentReference);
    myCollectionRole = null;
    myActualCollectionRole = role;
  }

  protected JpsElementChildRoleBase<? extends JpsElementCollection<? extends T>> getCollectionRole() {
    return myActualCollectionRole;
  }

  protected JpsNamedElementReferenceImpl(JpsNamedElementReferenceImpl<T, Self> original) {
    super(original);
    myCollectionRole = original.myCollectionRole;
    myActualCollectionRole = original.myActualCollectionRole;
  }

  @Override
  protected T resolve(T element) {
    return element;
  }

  @Override
  protected @Nullable JpsElementCollection<? extends T> getCollection(@NotNull JpsCompositeElement parent) {
    return parent.getContainer().getChild(myActualCollectionRole);
  }
}
