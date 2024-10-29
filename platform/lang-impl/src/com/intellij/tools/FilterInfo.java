// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.tools;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMExternalizable;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.Objects;

public final class FilterInfo implements JDOMExternalizable {
  private static final @NonNls String FILTER_NAME = "NAME";
  private static final @NonNls String FILTER_DESCRIPTION = "DESCRIPTION";
  private static final @NonNls String FILTER_REGEXP = "REGEXP";

  private String myName = ToolsBundle.message("tools.filters.name.default");
  private String myDescription;
  private String myRegExp;
  private static final @NonNls String ELEMENT_OPTION = "option";
  private static final @NonNls String ATTRIBUTE_VALUE = "value";
  private static final @NonNls String ATTRIBUTE_NAME = "name";

  public FilterInfo() {
  }

  public FilterInfo(String regExp, String name, String description) {
    myRegExp = regExp;
    myName = name;
    myDescription = description;
  }

  public String getDescription() {
    return myDescription;
  }

  public void setDescription(String description) {
    myDescription = description;
  }

  public String getName() {
    return myName;
  }

  public void setName(String name) {
    myName = name;
  }

  public String getRegExp() {
    return myRegExp;
  }

  public void setRegExp(String regExp) {
    myRegExp = regExp;
  }

  public int hashCode() {
    return Comparing.hashcode(myName) +
           Comparing.hashcode(myDescription) +
           Comparing.hashcode(myRegExp);
  }

  public boolean equals(Object object) {
    if (!(object instanceof FilterInfo other)) return false;
    return Objects.equals(myName, other.myName) &&
           Objects.equals(myDescription, other.myDescription) &&
           Objects.equals(myRegExp, other.myRegExp);
  }

  public FilterInfo createCopy() {
    return new FilterInfo(myRegExp, myName, myDescription);
  }

  @Override
  public void readExternal(Element element) {
    for (Element optionElement : element.getChildren(ELEMENT_OPTION)) {
      String value = optionElement.getAttributeValue(ATTRIBUTE_VALUE);
      String name = optionElement.getAttributeValue(ATTRIBUTE_NAME);

      if (FILTER_NAME.equals(name)) {
        if (value != null) {
          myName = convertString(value);
        }
      }
      if (FILTER_DESCRIPTION.equals(name)) {
        myDescription = convertString(value);
      }
      if (FILTER_REGEXP.equals(name)) {
        myRegExp = convertString(value);
      }
    }
  }

  @Override
  public void writeExternal(Element filterElement) {
    Element option = new Element(ELEMENT_OPTION);
    filterElement.addContent(option);
    option.setAttribute(ATTRIBUTE_NAME, FILTER_NAME);
    if (myName != null) {
      option.setAttribute(ATTRIBUTE_VALUE, myName);
    }

    option = new Element(ELEMENT_OPTION);
    filterElement.addContent(option);
    option.setAttribute(ATTRIBUTE_NAME, FILTER_DESCRIPTION);
    if (myDescription != null) {
      option.setAttribute(ATTRIBUTE_VALUE, myDescription);
    }

    option = new Element(ELEMENT_OPTION);
    filterElement.addContent(option);
    option.setAttribute(ATTRIBUTE_NAME, FILTER_REGEXP);
    if (myRegExp != null) {
      option.setAttribute(ATTRIBUTE_VALUE, myRegExp);
    }
  }

  public static String convertString(String s) {
    return ToolManager.convertString(s);
  }
}
