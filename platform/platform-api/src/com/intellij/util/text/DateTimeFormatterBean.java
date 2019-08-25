// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.text;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.util.xmlb.annotations.Attribute;

/**
 * @author Konstantin Bulenkov
 */
public class DateTimeFormatterBean {
  public static final ExtensionPointName<DateTimeFormatterBean> EP_NAME = ExtensionPointName.create("com.intellij.dateTimeFormatter");

  @Attribute("id")
  @RequiredElement
  public String id;

  @Attribute("name")
  @RequiredElement
  public String name;

  @Attribute("format")
  public String format;
}
