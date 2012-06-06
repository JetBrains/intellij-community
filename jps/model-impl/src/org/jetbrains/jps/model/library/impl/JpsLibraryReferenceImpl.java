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
  public JpsLibraryReferenceImpl(JpsModel model, JpsEventDispatcher eventDispatcher, String elementName, JpsElementReference<? extends JpsCompositeElement> parentReference,
                                 JpsParentElement parent) {
    super(model, eventDispatcher, JpsLibraryKind.LIBRARIES_COLLECTION_KIND, elementName, parentReference, parent);
  }

  public JpsLibraryReferenceImpl(JpsLibraryReferenceImpl original, JpsModel model, JpsEventDispatcher eventDispatcher, JpsParentElement parent) {
    super(original, model, eventDispatcher, parent);
  }

  @NotNull
  @Override
  public String getLibraryName() {
    return myElementName;
  }

  @NotNull
  @Override
  public JpsLibraryReferenceImpl createCopy(@NotNull JpsModel model,
                                            @NotNull JpsEventDispatcher eventDispatcher,
                                            JpsParentElement parent) {
    return new JpsLibraryReferenceImpl(this, model, eventDispatcher, parent);
  }
}
