/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.fileTypes;

import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.util.xmlb.annotations.Attribute;

public class SyntaxHighlighterFactoryEP extends LanguageExtensionPoint<SyntaxHighlighterFactory> {
  // For backward compatibility

  /**
   * @deprecated use "language" attribute instead
   */
  @Deprecated
  @Attribute("key")
  public String key;

  @Override
  public String getKey() {
    final String result = super.getKey();
    if (result != null) {
      return result;
    }

    //noinspection deprecation
    return key;
  }
}
