package com.intellij.ide.hierarchy;

import com.intellij.lang.LanguageExtension;

/**
 * Provides language-specific implementations of type hierarchy providers.
 *
 * @author yole
 */
public class LanguageTypeHierarchy extends LanguageExtension<HierarchyProvider> {
  public static final LanguageTypeHierarchy INSTANCE = new LanguageTypeHierarchy();

  public LanguageTypeHierarchy() {
    super("com.intellij.typeHierarchyProvider");
  }
}