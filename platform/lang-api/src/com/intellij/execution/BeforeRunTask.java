package com.intellij.execution;

import org.jdom.Element;

/**
 * @author Eugene Zhuravlev
 *         Date: May 18, 2009
 */
public abstract class BeforeRunTask implements Cloneable{
  private boolean myIsEnabled;

  public boolean isEnabled() {
    return myIsEnabled;
  }

  public void setEnabled(boolean isEnabled) {
    myIsEnabled = isEnabled;
  }

  public void writeExternal(Element element) {
    element.setAttribute("enabled", String.valueOf(myIsEnabled));
  }
  
  public void readExternal(Element element) {
    String attribValue = element.getAttributeValue("enabled");
    if (attribValue == null) {
      attribValue = element.getAttributeValue("value"); // maintain compatibility with old format
    }
    myIsEnabled = Boolean.valueOf(attribValue).booleanValue();
  }

  public BeforeRunTask clone() {
    try {
      return (BeforeRunTask)super.clone();
    }
    catch (CloneNotSupportedException ignored) {
      return null;
    }
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    BeforeRunTask that = (BeforeRunTask)o;

    if (myIsEnabled != that.myIsEnabled) return false;

    return true;
  }

  public int hashCode() {
    return (myIsEnabled ? 1 : 0);
  }
}
