// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.xmlb.annotations.Attribute;

public class IconDescriptionBundleEP {
  public static final ExtensionPointName<IconDescriptionBundleEP> EP_NAME = ExtensionPointName.create("com.intellij.iconDescriptionBundle");

  @Attribute("qualifiedName")
  public String qualifiedName;
}
