/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.openapi.fileTypes;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.util.KeyedLazyInstance;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class SyntaxHighlighterLanguageFactory extends LanguageExtension<SyntaxHighlighterFactory> {
  public static final ExtensionPointName<KeyedLazyInstance<SyntaxHighlighterFactory>> EP_NAME = ExtensionPointName.create("com.intellij.lang.syntaxHighlighterFactory");

  private boolean myEPListenerAdded = false;

  SyntaxHighlighterLanguageFactory() {
    super(EP_NAME, new PlainSyntaxHighlighterFactory());
  }

  @NotNull
  @Override
  protected List<SyntaxHighlighterFactory> buildExtensions(@NotNull String stringKey, @NotNull Language key) {
    List<SyntaxHighlighterFactory> fromEP = super.buildExtensions(stringKey, key);
    if (fromEP.isEmpty()) {
      SyntaxHighlighter highlighter = LanguageSyntaxHighlighters.INSTANCE.forLanguage(key);
      if (highlighter != null) {
        checkAddEPListener();
        SyntaxHighlighterFactory defaultFactory = new SingleLazyInstanceSyntaxHighlighterFactory() {
          @NotNull
          @Override
          protected SyntaxHighlighter createHighlighter() {
            return highlighter;
          }
        };
        return Collections.singletonList(defaultFactory);
      }
    }
    return fromEP;
  }

  private synchronized void checkAddEPListener() {
    if (!myEPListenerAdded) {
      myEPListenerAdded = true;

      LanguageSyntaxHighlighters.EP_NAME.addExtensionPointListener(new ExtensionPointListener<KeyedLazyInstance<SyntaxHighlighter>>() {
        @Override
        public void extensionAdded(@NotNull KeyedLazyInstance<SyntaxHighlighter> extension, @NotNull PluginDescriptor pluginDescriptor) {
          invalidateCacheForExtension(extension.getKey());
        }

        @Override
        public void extensionRemoved(@NotNull KeyedLazyInstance<SyntaxHighlighter> extension, @NotNull PluginDescriptor pluginDescriptor) {
          invalidateCacheForExtension(extension.getKey());
        }
      }, null);
    }
  }
}
