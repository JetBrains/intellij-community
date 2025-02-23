// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.SmartList;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;

// A way to remove obsolete component data.
@ApiStatus.Internal
public final class ObsoleteStorageBean {
  public static final ExtensionPointName<ObsoleteStorageBean> EP_NAME = new ExtensionPointName<>("com.intellij.obsoleteStorage");

  @Attribute
  public String file;

  @Attribute
  public boolean isProjectLevel;

  @XCollection(propertyElementName = "components", style = XCollection.Style.v2, elementName = "component", valueAttributeName = "")
  public final List<String> components = new SmartList<>();
}
