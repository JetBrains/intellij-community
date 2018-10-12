// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.components.OldComponentConfig;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jdom.Element;

public class PluginBean extends OptimizedPluginBean {
  @XCollection(propertyElementName = APPLICATION_COMPONENTS)
  public OldComponentConfig[] applicationComponents;

  @XCollection(propertyElementName = PROJECT_COMPONENTS)
  public OldComponentConfig[] projectComponents;

  @XCollection(propertyElementName = MODULE_COMPONENTS)
  public OldComponentConfig[] moduleComponents;

  @Tag("actions")
  public Element[] actions;

  @Tag("extensions")
  public Element[] extensions;

  @Tag("extensionPoints")
  public Element[] extensionPoints;
}
