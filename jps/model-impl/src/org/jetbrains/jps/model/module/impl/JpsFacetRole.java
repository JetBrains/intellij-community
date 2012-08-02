package org.jetbrains.jps.model.module.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsEventDispatcher;
import org.jetbrains.jps.model.impl.JpsElementCollectionRole;
import org.jetbrains.jps.model.impl.JpsElementChildRoleBase;
import org.jetbrains.jps.model.module.JpsFacet;
import org.jetbrains.jps.model.module.JpsFacetListener;

/**
 * @author nik
 */
public class JpsFacetRole extends JpsElementChildRoleBase<JpsFacet> {
  public static final JpsFacetRole INSTANCE = new JpsFacetRole();
  public static final JpsElementCollectionRole<JpsFacet> COLLECTION_ROLE = JpsElementCollectionRole.create(INSTANCE);

  public JpsFacetRole() {
    super("facet");
  }

  @Override
  public void fireElementAdded(@NotNull JpsEventDispatcher dispatcher, @NotNull JpsFacet element) {
    dispatcher.getPublisher(JpsFacetListener.class).facetAdded(element);
  }

  @Override
  public void fireElementRemoved(@NotNull JpsEventDispatcher dispatcher, @NotNull JpsFacet element) {
    dispatcher.getPublisher(JpsFacetListener.class).facetRemoved(element);
  }
}
