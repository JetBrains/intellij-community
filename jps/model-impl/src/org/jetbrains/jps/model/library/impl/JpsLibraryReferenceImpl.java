// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.library.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsCompositeElement;
import org.jetbrains.jps.model.JpsElementReference;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.impl.JpsNamedElementReferenceImpl;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryReference;

public final class JpsLibraryReferenceImpl extends JpsNamedElementReferenceImpl<JpsLibrary, JpsLibraryReferenceImpl> implements JpsLibraryReference {
  public JpsLibraryReferenceImpl(String elementName, JpsElementReference<? extends JpsCompositeElement> parentReference) {
    super(JpsLibraryRole.LIBRARIES_COLLECTION_ROLE, elementName, parentReference);
  }

  private JpsLibraryReferenceImpl(JpsLibraryReferenceImpl original) {
    super(original);
  }

  @Override
  public @NotNull String getLibraryName() {
    return myElementName;
  }

  @Override
  public @NotNull JpsLibraryReferenceImpl createCopy() {
    return new JpsLibraryReferenceImpl(this);
  }

  @Override
  public JpsLibraryReference asExternal(@NotNull JpsModel model) {
    model.registerExternalReference(this);
    return this;
  }

  @Override
  public String toString() {
    return "lib ref: '" + myElementName + "' in " + getParentReference();
  }
}
