package org.jetbrains.jps.model.module.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.JpsElementCollection;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.ex.JpsNamedCompositeElementBase;
import org.jetbrains.jps.model.module.JpsFacet;
import org.jetbrains.jps.model.module.JpsFacetReference;
import org.jetbrains.jps.model.module.JpsFacetType;
import org.jetbrains.jps.model.module.JpsModule;

/**
 * @author nik
 */
public class JpsFacetImpl extends JpsNamedCompositeElementBase<JpsFacetImpl> implements JpsFacet {
  private static final JpsElementChildRole<JpsFacetReference> PARENT_FACET_REFERENCE = JpsElementChildRoleBase.create("parent facet");
  private final JpsFacetType<?> myFacetType;

  public <P extends JpsElement>JpsFacetImpl(JpsFacetType<?> facetType, @NotNull String name, @NotNull P properties) {
    super(name);
    myFacetType = facetType;
    myContainer.setChild(JpsFacetRole.COLLECTION_ROLE);
  }

  private JpsFacetImpl(JpsFacetImpl original) {
    super(original);
    myFacetType = original.myFacetType;
  }

  @NotNull
  @Override
  public JpsFacetImpl createCopy() {
    return new JpsFacetImpl(this);
  }

  @Override
  @NotNull
  public JpsFacetType<?> getType() {
    return myFacetType;
  }

  @Override
  public void setParentFacet(@NotNull JpsFacet facet) {
    myContainer.setChild(PARENT_FACET_REFERENCE, facet.createReference());
  }

  @Override
  @Nullable
  public JpsFacet getParentFacet() {
    final JpsFacetReference reference = myContainer.getChild(PARENT_FACET_REFERENCE);
    return reference != null ? reference.resolve() : null;
  }

  @Override
  public JpsModule getModule() {
    return myParent != null ? (JpsModule)myParent.getParent() : null;
  }

  @NotNull
  @Override
  public JpsFacetReference createReference() {
    return new JpsFacetReferenceImpl(getName(), getModule().createReference());
  }

  @Override
  public void delete() {
    //noinspection unchecked
    ((JpsElementCollection<JpsFacet>)myParent).removeChild(this);
  }
}
