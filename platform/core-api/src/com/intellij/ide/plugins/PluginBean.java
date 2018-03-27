/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.ide.plugins;

import com.intellij.openapi.components.OldComponentConfig;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;

public class PluginBean {
  @XCollection(propertyElementName = APPLICATION_COMPONENTS)
  public OldComponentConfig[] applicationComponents;

  @XCollection(propertyElementName = PROJECT_COMPONENTS)
  public OldComponentConfig[] projectComponents;

  @XCollection(propertyElementName = MODULE_COMPONENTS)
  public OldComponentConfig[] moduleComponents;

  @NonNls public static final String APPLICATION_COMPONENTS = "application-components";
  @NonNls public static final String PROJECT_COMPONENTS = "project-components";
  @NonNls public static final String MODULE_COMPONENTS = "module-components";

  @Tag("name")
  public String name;

  @Tag("id")
  public String id;

  @Tag("description")
  public String description;

  @Attribute("version")
  public String formatVersion;

  @Tag("version")
  public String pluginVersion;

  @Property(surroundWithTag = false)
  public PluginVendor vendor;

  @Property(surroundWithTag = false)
  public IdeaVersionBean ideaVersion;

  @Tag(value = "is-internal", textIfEmpty = "true")
  public boolean isInternal;

  @Tag("extensions")
  public Element[] extensions;

  @Tag("extensionPoints")
  public Element[] extensionPoints;

  @Tag("actions")
  public Element[] actions;
                                     
  @Property(surroundWithTag = false)
  @XCollection
  public PluginDependency[] dependencies;

  @Property(surroundWithTag = false)
  @XCollection
  public PluginHelpSet[] helpSets;

  @Tag("category")
  public String category;

  @Tag("resource-bundle")
  public String resourceBundle;

  @Tag("change-notes")
  public String changeNotes;

  @Attribute("url")
  public String url;

  @Attribute("use-idea-classloader")
  public boolean useIdeaClassLoader;

  @Attribute("allow-bundled-update")
  public boolean allowBundledUpdate;

  @Property(surroundWithTag = false)
  @XCollection(elementName = "module")
  public List<String> modules = new ArrayList<>();
}
