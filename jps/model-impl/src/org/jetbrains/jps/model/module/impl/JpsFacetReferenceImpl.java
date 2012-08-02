package org.jetbrains.jps.model.module.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementReference;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.impl.JpsNamedElementReferenceBase;
import org.jetbrains.jps.model.module.JpsFacet;
import org.jetbrains.jps.model.module.JpsFacetReference;
import org.jetbrains.jps.model.module.JpsModuleReference;

/**
 * @author nik
 */
public class JpsFacetReferenceImpl extends JpsNamedElementReferenceBase<JpsFacet, JpsFacetReferenceImpl> implements JpsFacetReference {
  public JpsFacetReferenceImpl(String facetName, JpsModuleReference moduleReference) {
    super(JpsFacetRole.COLLECTION_ROLE, facetName, moduleReference);
  }

  private JpsFacetReferenceImpl(JpsFacetReferenceImpl original) {
    super(original);
  }

  @NotNull
  @Override
  public JpsFacetReferenceImpl createCopy() {
    return new JpsFacetReferenceImpl(this);
  }

  @Override
  public JpsElementReference<JpsFacet> asExternal(@NotNull JpsModel model) {
    model.registerExternalReference(this);
    return this;
  }
}
