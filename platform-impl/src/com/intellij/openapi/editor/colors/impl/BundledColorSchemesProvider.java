package com.intellij.openapi.editor.colors.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

/**
 * @author Roman Chernyatchik
 */
public interface BundledColorSchemesProvider {
  ExtensionPointName<BundledColorSchemesProvider> EP_NAME = ExtensionPointName.create("com.intellij.bundledColorSchemesProvider");

  /**
   * Provides relative pathes for schemes.
   * E.g. : ["/colorSchemes/colbalt", "myscheme"]
   * @return Array of relative pathes for schemes.
   */
  @NotNull
  String[] getBundledSchemesRelativePaths();
}
