package org.jetbrains.jps.javaee

/**
 * @author nik
 */
public class WebFacetType extends JavaeeFacetTypeBase {
  public WebFacetType() {
    super("web");
  }

  @Override
  protected String getDescriptorOutputPath(String descriptorId) {
    if (descriptorId == "context.xml") return "META-INF"
    return "WEB-INF"
  }


}
