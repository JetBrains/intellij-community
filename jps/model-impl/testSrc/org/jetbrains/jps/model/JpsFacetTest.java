package org.jetbrains.jps.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JpsJavaModuleType;
import org.jetbrains.jps.model.module.JpsFacet;
import org.jetbrains.jps.model.module.JpsFacetType;
import org.jetbrains.jps.model.module.JpsModule;

/**
 * @author nik
 */
public class JpsFacetTest extends JpsModelTestCase {
  public void testAddFacet() {
    final JpsModule m = myModel.getProject().addModule("m", JpsJavaModuleType.INSTANCE);
    m.addFacet("f", MY_FACET_TYPE, DummyJpsElementProperties.INSTANCE);
    assertEquals("f", assertOneElement(m.getFacets()).getName());
  }

  public void testCreateReferenceByFacet() {
    final JpsFacet facet = myModel.getProject().addModule("m", JpsJavaModuleType.INSTANCE).addFacet("f", MY_FACET_TYPE, DummyJpsElementProperties.INSTANCE);
    final JpsElementReference<JpsFacet> reference = facet.createReference().asExternal(myModel);
    assertSame(facet, reference.resolve());
  }

  private static final JpsFacetType<DummyJpsElementProperties> MY_FACET_TYPE = new JpsFacetType<DummyJpsElementProperties>() {
    @Override
    public DummyJpsElementProperties createCopy(DummyJpsElementProperties properties) {
      return DummyJpsElementProperties.INSTANCE;
    }

    @NotNull
    @Override
    public DummyJpsElementProperties createDefaultProperties() {
      return DummyJpsElementProperties.INSTANCE;
    }
  };
}
