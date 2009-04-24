package com.intellij.packaging.impl.artifacts;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jdom.Element;

/**
 * @author nik
 */
@Tag("artifact")
public class ArtifactState {
  private String myName;
  private String myOutputPath;
  private boolean myEnabled;
  private Element myRootElement;

  @Attribute("name")
  public String getName() {
    return myName;
  }

  @Attribute("enabled")
  public boolean isEnabled() {
    return myEnabled;
  }

  @Tag("output-path")
  public String getOutputPath() {
    return myOutputPath;
  }

  @Tag("root")
  public Element getRootElement() {
    return myRootElement;
  }

  public void setName(String name) {
    myName = name;
  }

  public void setOutputPath(String outputPath) {
    myOutputPath = outputPath;
  }

  public void setEnabled(boolean enabled) {
    myEnabled = enabled;
  }

  public void setRootElement(Element rootElement) {
    myRootElement = rootElement;
  }
}
