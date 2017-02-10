/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.util.JDOMExternalizerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class BasicRendererProperties implements Cloneable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.tree.render.BasicRendererProperties");

  private static final @NonNls String NAME_OPTION = "NAME";
  private String myName;

  private static final @NonNls String ENABLED_OPTION = "ENABLED";
  private boolean myEnabled;

  private static final @NonNls String CLASSNAME_OPTION = "QUALIFIED_NAME";
  private String myClassName;

  private static final @NonNls String SHOW_TYPE_OPTION = "SHOW_TYPE";
  private boolean myShowType = true;

  private final boolean myEnabledDefaultValue;

  public BasicRendererProperties(boolean enabledDefaultValue) {
    myEnabledDefaultValue = enabledDefaultValue;
  }

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

  public void readExternal(Element element) {
    myName = null;
    myClassName = null;
    for (Element option : element.getChildren("option")) {
      final String optionName = option.getAttributeValue("name");
      switch (optionName) {
        case NAME_OPTION:
          myName = option.getAttributeValue("value");
          break;
        case ENABLED_OPTION:
          // default is false
          String value = option.getAttributeValue("value");
          if (value != null) {
            myEnabled = Boolean.parseBoolean(value);
          }
          break;
        case CLASSNAME_OPTION:
          myClassName = option.getAttributeValue("value");
          break;
        case SHOW_TYPE_OPTION:
          // default is true
          myShowType = !"false".equalsIgnoreCase(option.getAttributeValue("value"));
          break;
      }
    }
  }

  public void writeExternal(@NotNull Element element) {
    if (myName != null) {
      JDOMExternalizerUtil.writeField(element, NAME_OPTION, myName);
    }
    if (myEnabled != myEnabledDefaultValue) {
      JDOMExternalizerUtil.writeField(element, ENABLED_OPTION, Boolean.toString(myEnabled));
    }
    if (myClassName != null) {
      JDOMExternalizerUtil.writeField(element, CLASSNAME_OPTION, myClassName);
    }
    if (!myShowType) {
      // default is true
      //noinspection ConstantConditions
      JDOMExternalizerUtil.writeField(element, SHOW_TYPE_OPTION, Boolean.toString(myShowType));
    }
  }

  @Override
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
