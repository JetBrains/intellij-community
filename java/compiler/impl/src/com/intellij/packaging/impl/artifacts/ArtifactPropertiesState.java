package com.intellij.packaging.impl.artifacts;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jdom.Element;

/**
 * @author nik
 */
@Tag("properties")
public class ArtifactPropertiesState {
  private String myId;
  private Element myOptions;

  @Attribute("id")
  public String getId() {
    return myId;
  }

  @Tag("options")
  public Element getOptions() {
    return myOptions;
  }

  public void setId(String id) {
    myId = id;
  }

  public void setOptions(Element options) {
    myOptions = options;
  }
}
