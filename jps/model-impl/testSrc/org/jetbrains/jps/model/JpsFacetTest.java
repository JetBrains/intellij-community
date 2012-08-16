package org.jetbrains.jps.model;

import org.jetbrains.jps.model.java.JpsJavaModuleType;
import org.jetbrains.jps.model.module.JpsFacet;
import org.jetbrains.jps.model.module.JpsFacetType;
import org.jetbrains.jps.model.module.JpsModule;

/**
 * @author nik
 */
public class JpsFacetTest extends JpsModelTestCase {
  public void testAddFacet() {
    final JpsModule m = myProject.addModule("m", JpsJavaModuleType.INSTANCE);
    m.addFacet("f", MY_FACET_TYPE, JpsElementFactory.getInstance().createDummyElement());
    assertEquals("f", assertOneElement(m.getFacets()).getName());
  }

  public void testCreateReferenceByFacet() {
    final JpsFacet facet = myProject.addModule("m", JpsJavaModuleType.INSTANCE).addFacet("f", MY_FACET_TYPE, JpsElementFactory.getInstance().createDummyElement());
    final JpsElementReference<JpsFacet> reference = facet.createReference().asExternal(myModel);
    assertSame(facet, reference.resolve());
  }

  private static final JpsFacetType<JpsDummyElement> MY_FACET_TYPE = new JpsFacetType<JpsDummyElement>() { };
}
