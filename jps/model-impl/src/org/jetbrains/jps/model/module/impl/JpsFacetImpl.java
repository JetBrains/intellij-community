package org.jetbrains.jps.model.module.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.JpsElementCollection;
import org.jetbrains.jps.model.JpsElementProperties;
import org.jetbrains.jps.model.impl.*;
import org.jetbrains.jps.model.impl.JpsElementChildRoleBase;
import org.jetbrains.jps.model.impl.JpsTypedDataRole;
import org.jetbrains.jps.model.module.JpsFacet;
import org.jetbrains.jps.model.module.JpsFacetReference;
import org.jetbrains.jps.model.module.JpsFacetType;
import org.jetbrains.jps.model.module.JpsModule;

/**
 * @author nik
 */
public class JpsFacetImpl extends JpsNamedCompositeElementBase<JpsFacetImpl> implements JpsFacet {
  private static final JpsTypedDataRole<JpsFacetType<?>> TYPED_DATA_ROLE = new JpsTypedDataRole<JpsFacetType<?>>();
  private static final JpsElementChildRole<JpsFacetReference> PARENT_FACET_REFERENCE = JpsElementChildRoleBase.create("parent facet");

  public <P extends JpsElementProperties>JpsFacetImpl(JpsFacetType<?> facetType, @NotNull String name, @NotNull P properties) {
    super(name);
    myContainer.setChild(TYPED_DATA_ROLE, new JpsTypedDataImpl<JpsFacetType<?>>(facetType, properties));
    myContainer.setChild(JpsFacetRole.COLLECTION_ROLE);
  }

  private JpsFacetImpl(JpsNamedCompositeElementBase<JpsFacetImpl> original) {
    super(original);
  }

  @NotNull
  @Override
  public JpsFacetImpl createCopy() {
    return new JpsFacetImpl(this);
  }

  @Override
  @NotNull
  public JpsFacetType<?> getType() {
    return myContainer.getChild(TYPED_DATA_ROLE).getType();
  }

  public <P extends JpsElementProperties> P getProperties(@NotNull JpsFacetType<P> type) {
    return myContainer.getChild(TYPED_DATA_ROLE).getProperties(type);
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
