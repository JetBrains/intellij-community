package com.intellij.execution.configurations;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public abstract class ConfigurationTypeBase implements ConfigurationType {
  private final String myId;
  private final String myDisplayName;
  private final String myDescription;
  private final Icon myIcon;
  private ConfigurationFactory[] myFactories;

  protected ConfigurationTypeBase(@NotNull String id, String displayName, String description, Icon icon) {
    myId = id;
    myDisplayName = displayName;
    myDescription = description;
    myIcon = icon;
    myFactories = new ConfigurationFactory[0];
  }

  protected void addFactory(ConfigurationFactory factory) {
    List<ConfigurationFactory> newFactories = new ArrayList<ConfigurationFactory>();
    Collections.addAll(newFactories, myFactories);
    newFactories.add(factory);
    myFactories = newFactories.toArray(new ConfigurationFactory[newFactories.size()]);
  }

  public String getDisplayName() {
    return myDisplayName;
  }

  public String getConfigurationTypeDescription() {
    return myDescription;
  }

  public Icon getIcon() {
    return myIcon;
  }

  @NotNull
  public String getId() {
    return myId;
  }

  public ConfigurationFactory[] getConfigurationFactories() {
    return myFactories;
  }
}
