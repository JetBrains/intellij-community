package com.intellij.ide.hierarchy;

import com.intellij.lang.LanguageExtension;

/**
 * Provides language-specific implementations of call hierarchy providers.
 *
 * @author yole
 */
public class LanguageCallHierarchy extends LanguageExtension<HierarchyProvider> {
  public static final LanguageCallHierarchy INSTANCE = new LanguageCallHierarchy();

  public LanguageCallHierarchy() {
    super("com.intellij.callHierarchyProvider");
  }
}
