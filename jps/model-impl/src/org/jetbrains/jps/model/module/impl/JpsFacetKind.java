package org.jetbrains.jps.model.module.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsEventDispatcher;
import org.jetbrains.jps.model.impl.JpsElementCollectionKind;
import org.jetbrains.jps.model.impl.JpsElementKindBase;
import org.jetbrains.jps.model.module.JpsFacet;
import org.jetbrains.jps.model.module.JpsFacetListener;

/**
 * @author nik
 */
public class JpsFacetKind extends JpsElementKindBase<JpsFacet> {
  public static final JpsFacetKind INSTANCE = new JpsFacetKind();
  public static final JpsElementCollectionKind<JpsFacet> COLLECTION_KIND = JpsElementCollectionKind.create(INSTANCE);

  public JpsFacetKind() {
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
