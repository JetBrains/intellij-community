package com.intellij.util.descriptors;

/**
 * @author Gregory.Shrago
 */
public abstract class ConfigFileAdapter implements ConfigFileListener {
  protected void configChanged(ConfigFile configFile) {
  }

  public void configFileAdded(ConfigFile configFile) {
    configChanged(configFile);
  }

  public void configFileRemoved(ConfigFile configFile) {
    configChanged(configFile);
  }

  public void configFileChanged(ConfigFile configFile) {
    configChanged(configFile);
  }
}
