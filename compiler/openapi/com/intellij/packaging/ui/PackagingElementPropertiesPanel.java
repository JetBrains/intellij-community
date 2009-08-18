package com.intellij.packaging.ui;

import com.intellij.openapi.options.UnnamedConfigurable;

/**
 * @author nik
 */
public abstract class PackagingElementPropertiesPanel implements UnnamedConfigurable {

  public abstract void apply();

  public void disposeUIResources() {
  }
}
