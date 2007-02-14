package com.intellij.openapi.components;

import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NonNls;

public class ComponentManagerConfig {
  @Tag(APPLICATION_COMPONENTS)
  @AbstractCollection(surroundWithTag = false)
  public ComponentConfig[] applicationComponents;

  @Tag(PROJECT_COMPONENTS)
  @AbstractCollection(surroundWithTag = false)
  public ComponentConfig[] projectComponents;

  @Tag(MODULE_COMPONENTS)
  @AbstractCollection(surroundWithTag = false)
  public ComponentConfig[] moduleComponents;

  @NonNls public static final String APPLICATION_COMPONENTS = "application-components";
  @NonNls public static final String PROJECT_COMPONENTS = "project-components";
  @NonNls public static final String MODULE_COMPONENTS = "module-components";
}
