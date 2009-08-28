package com.intellij.facet.ui;

import com.intellij.openapi.options.UnnamedConfigurable;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

/**
 * @author nik
 */
public abstract class DefaultFacetSettingsEditor implements UnnamedConfigurable {
  @Nullable @NonNls
  public String getHelpTopic() {
    return null;
  }
}
