// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.folding;

import com.intellij.codeInsight.folding.CodeFoldingSettings;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Base class and extension point for custom folding providers.
 *
 * @author Rustam Vishnyakov
 */
public abstract class CustomFoldingProvider {
  public static final ExtensionPointName<CustomFoldingProvider> EP_NAME = ExtensionPointName.create("com.intellij.customFoldingProvider");

  @NotNull
  public static List<CustomFoldingProvider> getAllProviders() {
    return EP_NAME.getExtensionList();
  }

  public abstract boolean isCustomRegionStart(String elementText);
  public abstract boolean isCustomRegionEnd(String elementText);
  public abstract String getPlaceholderText(String elementText);

  /**
   * @return A description string shown in "Surround With" action.
   */
  public abstract String getDescription();

  public abstract String getStartString();
  public abstract String getEndString();

  public boolean isCollapsedByDefault(String text) {
    return CodeFoldingSettings.getInstance().COLLAPSE_CUSTOM_FOLDING_REGIONS;
  }
}
