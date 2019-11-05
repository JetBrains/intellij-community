// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.components.OldComponentConfig;
import com.intellij.util.SmartList;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jdom.Element;

import java.util.List;

public class PluginBean extends OptimizedPluginBean {
  @Tag("id")
  public String id;

  @Tag("name")
  public String name;

  @Tag("version")
  public String pluginVersion;

  @Attribute("url")
  public String url;

  @Tag("category")
  public String category;

  @Attribute("version")
  public String formatVersion;

  @Tag("change-notes")
  public String changeNotes;

  @Tag("resource-bundle")
  public String resourceBundle;

  @Tag("description")
  public String description;

  @Property(surroundWithTag = false)
  public PluginVendor vendor;

  @Property(surroundWithTag = false)
  public ProductDescriptor productDescriptor;

  @XCollection(propertyElementName = "application-components")
  public OldComponentConfig[] applicationComponents;

  @XCollection(propertyElementName = "project-components")
  public OldComponentConfig[] projectComponents;

  @XCollection(propertyElementName = "module-components")
  public OldComponentConfig[] moduleComponents;

  @Tag("actions")
  public Element[] actions;

  @Tag("extensions")
  public Element[] extensions;

  @Tag("extensionPoints")
  public Element[] extensionPoints;

  @Property(surroundWithTag = false)
  @XCollection(elementName = "module")
  public List<String> modules = new SmartList<>();

  @Property(surroundWithTag = false)
  @XCollection
  public PluginDependency[] dependencies;

  @Attribute("use-idea-classloader")
  public boolean useIdeaClassLoader;

  @Attribute("allow-bundled-update")
  public boolean allowBundledUpdate;

  @Attribute("implementation-detail")
  public boolean implementationDetail;

  @Property(surroundWithTag = false)
  public IdeaVersionBean ideaVersion;
}
