package com.intellij.ide;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.UnnamedConfigurable;

/**
 * @author spleaner
 */
public abstract class BrowserSettingsProvider implements UnnamedConfigurable {
  public static ExtensionPointName<BrowserSettingsProvider> EP_NAME =
      ExtensionPointName.create("com.intellij.browserSettingsProvider");

  public void disposeUIResources() {
  }
}
