// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.ex.JpsCompositeElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;

import java.util.List;

public abstract class JpsNamedElementReferenceBase<S extends JpsNamedElement, T extends JpsNamedElement, Self extends JpsNamedElementReferenceBase<S, T, Self>>
  extends JpsCompositeElementBase<Self> implements JpsElementReference<T> {
  private static final JpsElementChildRole<JpsElementReference<? extends JpsCompositeElement>> PARENT_REFERENCE_ROLE = JpsElementChildRoleBase
    .create("parent");
  protected final @NotNull String myElementName;

  protected JpsNamedElementReferenceBase(@NotNull String elementName, @NotNull JpsElementReference<? extends JpsCompositeElement> parentReference) {
    myElementName = elementName;
    myContainer.setChild(PARENT_REFERENCE_ROLE, parentReference);
  }

  /**
   * @deprecated creating copies isn't supported in for all elements in JPS anymore; if you need to create a copy for your element,
   * write the corresponding code in your class directly.
   */
  @Deprecated
  protected JpsNamedElementReferenceBase(JpsNamedElementReferenceBase<S, T, Self> original) {
    super(original);
    myElementName = original.myElementName;
  }

  @Override
  public T resolve() {
    final JpsCompositeElement parent = getParentReference().resolve();
    if (parent == null) return null;

    JpsElementCollection<? extends S> collection = getCollection(parent);
    if (collection == null) return null;

    if (collection instanceof JpsNamedElementCollection<?>) {
      S element = ((JpsNamedElementCollection<? extends S>)collection).findChild(myElementName);
      return element != null ? resolve(element) : null;
    }

    final List<? extends S> elements = collection.getElements();
    for (S element : elements) {
      if (element.getName().equals(myElementName)) {
        T resolved = resolve(element);
        if (resolved != null) {
          return resolved;
        }
      }
    }
    return null;
  }

  /**
   * @deprecated override {@link #getNamedElementCollection} instead to speed up {@link #resolve()}
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  protected @Nullable JpsElementCollection<? extends S> getCollection(@NotNull JpsCompositeElement parent) {
    return getNamedElementCollection(parent);
  }
  
  protected @Nullable JpsNamedElementCollection<? extends S> getNamedElementCollection(@NotNull JpsCompositeElement parent) {
    return null;
  }

  protected abstract @Nullable T resolve(S element);

  public JpsElementReference<? extends JpsCompositeElement> getParentReference() {
    return myContainer.getChild(PARENT_REFERENCE_ROLE);
  }

  @Override
  public JpsElementReference<T> asExternal(@NotNull JpsModel model) {
    model.registerExternalReference(this);
    return this;
  }
}
