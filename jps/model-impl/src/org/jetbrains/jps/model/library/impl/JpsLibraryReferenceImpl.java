package org.jetbrains.jps.model.library.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.impl.JpsNamedElementReferenceBase;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryReference;

/**
 * @author nik
 */
public class JpsLibraryReferenceImpl extends JpsNamedElementReferenceBase<JpsLibrary, JpsLibraryReferenceImpl> implements JpsLibraryReference {
  public JpsLibraryReferenceImpl(String elementName, JpsElementReference<? extends JpsCompositeElement> parentReference) {
    super(JpsLibraryRole.LIBRARIES_COLLECTION_ROLE, elementName, parentReference);
  }

  private JpsLibraryReferenceImpl(JpsLibraryReferenceImpl original) {
    super(original);
  }

  @NotNull
  @Override
  public String getLibraryName() {
    return myElementName;
  }

  @NotNull
  @Override
  public JpsLibraryReferenceImpl createCopy() {
    return new JpsLibraryReferenceImpl(this);
  }

  @Override
  public JpsLibraryReference asExternal(@NotNull JpsModel model) {
    model.registerExternalReference(this);
    return this;
  }
}
