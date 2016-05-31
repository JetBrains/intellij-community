/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle.lineIndent;

import com.intellij.lang.Language;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.codeStyle.lineIndent.LineIndentProvider;
import org.jetbrains.annotations.Nullable;

/**
 * Line indent provider extension point
 */
public class LineIndentProviderEP {
  public final static ExtensionPointName<LineIndentProvider> EP_NAME = ExtensionPointName.create("com.intellij.lineIndentProvider");
  
  @Nullable
  public static LineIndentProvider findLineIndentProvider(@Nullable Language language) {
    LineIndentProvider foundProvider = null;
    for (LineIndentProvider provider : EP_NAME.getExtensions()) {
      if (foundProvider == null || provider.isSuitableFor(language) && foundProvider.getClass().isInstance(provider)) {
        foundProvider = provider;
      }
    }
    return foundProvider;
  }
}
