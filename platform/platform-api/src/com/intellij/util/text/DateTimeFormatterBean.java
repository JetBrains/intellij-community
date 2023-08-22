// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.text;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;

/**
 * @author Konstantin Bulenkov
 */
public class DateTimeFormatterBean {
  public static final ExtensionPointName<DateTimeFormatterBean> EP_NAME = ExtensionPointName.create("com.intellij.dateTimeFormatter");

  @Attribute("id")
  @RequiredElement
  public @NonNls String id;

  @Attribute("name")
  @RequiredElement
  public @Nls String name;

  @Attribute("format")
  public @Nls String format;
}
