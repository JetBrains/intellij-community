/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.ui.tree.render;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

/**
 * @author Eugene Zhuravlev
 *         Date: Feb 12, 2005
 */
public final class BasicRendererProperties implements Cloneable, JDOMExternalizable{
  // todo: add class filters here
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.tree.render.BasicRendererProperties");

  private static final @NonNls String NAME_OPTION = "NAME";
  private String myName;

  private static final @NonNls String ENABLED_OPTION = "ENABLED";
  private boolean myEnabled;

  private static final @NonNls String CLASSNAME_OPTION = "QUALIFIED_NAME";
  private String myClassName;

  private static final @NonNls String SHOWTYPE_OPTION = "SHOW_TYPE";
  private boolean myShowType = true;

  public String getName() {
    return myName;
  }

  public void setName(final String name) {
    myName = name;
  }

  public boolean isEnabled() {
    return myEnabled;
  }

  public void setEnabled(final boolean enabled) {
    myEnabled = enabled;
  }

  public String getClassName() {
    return myClassName;
  }

  public void setClassName(final String className) {
    myClassName = className;
  }

  public boolean isShowType() {
    return myShowType;
  }

  public void setShowType(boolean showType) {
    myShowType = showType;
  }

  @SuppressWarnings({"HardCodedStringLiteral"}) public void readExternal(Element element) throws InvalidDataException {
    myName = null;
    myClassName = null;
    for (Element option : element.getChildren("option")) {
      final String optionName = option.getAttributeValue("name");
      if (NAME_OPTION.equals(optionName)) {
        myName = option.getAttributeValue("value");
      }
      else if (ENABLED_OPTION.equals(optionName)) {
        // default is false
        myEnabled = Boolean.parseBoolean(option.getAttributeValue("value"));
      }
      else if (CLASSNAME_OPTION.equals(optionName)) {
        myClassName = option.getAttributeValue("value");
      }
      else if (SHOWTYPE_OPTION.equals(optionName)) {
        // default is true
        myShowType = !"false".equalsIgnoreCase(option.getAttributeValue("value"));
      }
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"}) public void writeExternal(Element element) throws WriteExternalException {
    if (myName != null) {
      addOption(element, NAME_OPTION, myName);
    }
    if (myEnabled) {
      // default is false
      //noinspection ConstantConditions
      addOption(element, ENABLED_OPTION, Boolean.toString(myEnabled));
    }
    if (myClassName != null) {
      addOption(element, CLASSNAME_OPTION, myClassName);
    }
    if (!myShowType) {
      // default is true
      //noinspection ConstantConditions
      addOption(element, SHOWTYPE_OPTION, Boolean.toString(myShowType));
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static void addOption(final Element element, final String optionName, final String optionValue) {
    final Element option = new Element("option");
    element.addContent(option);
    option.setAttribute("name", optionName);
    option.setAttribute("value", optionValue);
  }

  public BasicRendererProperties clone()  {
    try {
      return (BasicRendererProperties)super.clone();
    }
    catch (CloneNotSupportedException e) {
      LOG.error(e);
    }
    return null;
  }
}
