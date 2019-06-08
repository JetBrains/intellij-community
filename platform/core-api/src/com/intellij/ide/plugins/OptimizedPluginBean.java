// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;

public class OptimizedPluginBean {
  public static final String APPLICATION_COMPONENTS = "application-components";
  public static final String PROJECT_COMPONENTS = "project-components";
  public static final String MODULE_COMPONENTS = "module-components";

  @Tag("name")
  public String name;

  @Tag("id")
  public String id;

  @Property(surroundWithTag = false)
  public ProductDescriptor productDescriptor;

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

  @Property(surroundWithTag = false)
  @XCollection
  public PluginDependency[] dependencies;

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

  @Attribute("implementation-detail")
  public boolean implementationDetail;
}