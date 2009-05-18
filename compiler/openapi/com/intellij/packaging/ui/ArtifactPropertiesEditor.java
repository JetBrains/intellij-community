package com.intellij.packaging.ui;

import com.intellij.openapi.options.UnnamedConfigurable;

/**
 * @author nik
 */
public abstract class ArtifactPropertiesEditor implements UnnamedConfigurable {
  protected static final String VALIDATION_TAB = "Validation";  
  protected static final String POSTPROCESSING_TAB = "Post-processing";

  public abstract String getTabName();

  public abstract void apply();
}
