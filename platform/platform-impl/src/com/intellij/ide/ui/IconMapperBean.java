// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;

/**
 * @author Konstantin Bulenkov
 */
public final class IconMapperBean {
  @ApiStatus.Internal
  public static final ExtensionPointName<IconMapperBean> EP_NAME = new ExtensionPointName<>("com.intellij.iconMapper");

  @Attribute("mappingFile")
  @RequiredElement
  public @NonNls String mappingFile;
}
