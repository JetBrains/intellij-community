package com.intellij.ide.plugins;

import com.intellij.openapi.components.ComponentManagerConfig;
import com.intellij.util.xmlb.annotations.*;
import org.jdom.Element;

public class PluginBean extends ComponentManagerConfig {
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
  public Element actions;
                                     
  @Property(surroundWithTag = false)
  @AbstractCollection(surroundWithTag = false)
  public PluginDependency[] dependencies;

  @Property(surroundWithTag = false)
  @AbstractCollection(surroundWithTag = false)
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
}
