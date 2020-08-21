// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.tree.render;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.NlsSafe;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class BasicRendererProperties implements Cloneable {
  private static final Logger LOG = Logger.getInstance(BasicRendererProperties.class);

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

  public @NlsSafe String getName() {
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

  public void readExternal(@NotNull Element element, @Nullable String defaultName) {
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

    if (myName == null) {
      myName = defaultName;
    }
  }

  public void writeExternal(@NotNull Element element, @Nullable String defaultName) {
    if (myName != null && !myName.equals(defaultName)) {
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
