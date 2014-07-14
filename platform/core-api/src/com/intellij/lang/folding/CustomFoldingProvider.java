/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.folding;

import com.intellij.codeInsight.folding.CodeFoldingSettings;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;

/**
 * Base class and extension point for custom folding providers.
 *
 * @author Rustam Vishnyakov
 */
public abstract class CustomFoldingProvider {
  public static final ExtensionPointName<CustomFoldingProvider> EP_NAME = ExtensionPointName.create("com.intellij.customFoldingProvider");

  public static CustomFoldingProvider[] getAllProviders() {
    return Extensions.getExtensions(EP_NAME);
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
