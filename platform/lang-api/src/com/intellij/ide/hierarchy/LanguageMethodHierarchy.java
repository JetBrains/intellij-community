package com.intellij.ide.hierarchy;

import com.intellij.lang.LanguageExtension;

/**
 * Provides language-specific implementations of method hierarchy providers.
 *
 * @author yole
 */
public class LanguageMethodHierarchy extends LanguageExtension<HierarchyProvider> {
  public static final LanguageMethodHierarchy INSTANCE = new LanguageMethodHierarchy();

  public LanguageMethodHierarchy() {
    super("com.intellij.methodHierarchyProvider");
  }
}
