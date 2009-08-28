package com.intellij.openapi.module;

/**
 * @author nik
 */
public abstract class ConfigurationErrorDescription {
  private final String myElementName;
  private final String myElementKind;
  private final String myDescription;

  protected ConfigurationErrorDescription(String elementName, String elementKind, String description) {
    myElementName = elementName;
    myElementKind = elementKind;
    myDescription = description;
  }

  public String getElementName() {
    return myElementName;
  }

  public String getElementKind() {
    return myElementKind;
  }

  public String getDescription() {
    return myDescription;
  }

  public abstract void removeInvalidElement();

  public abstract String getRemoveConfirmationMessage();

  public boolean isValid() {
    return true;
  }
}
