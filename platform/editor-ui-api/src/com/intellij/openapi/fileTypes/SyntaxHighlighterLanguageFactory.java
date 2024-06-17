// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.ExtensionPointPriorityListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.util.KeyedLazyInstance;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class SyntaxHighlighterLanguageFactory extends LanguageExtension<SyntaxHighlighterFactory> {

  @ApiStatus.Internal
  public static final ExtensionPointName<KeyedLazyInstance<SyntaxHighlighterFactory>> EP_NAME = new ExtensionPointName<>("com.intellij.lang.syntaxHighlighterFactory");

  private boolean myEpListenerAdded = false;

  SyntaxHighlighterLanguageFactory() {
    super(EP_NAME, new PlainSyntaxHighlighterFactory());
  }

  @Override
  protected @NotNull List<SyntaxHighlighterFactory> buildExtensions(@NotNull String stringKey, @NotNull Language key) {
    List<SyntaxHighlighterFactory> fromEp = super.buildExtensions(stringKey, key);
    if (!fromEp.isEmpty()) {
      return fromEp;
    }

    SyntaxHighlighter highlighter = LanguageSyntaxHighlighters.getInstance().forLanguage(key);
    if (highlighter != null) {
      checkAddEPListener();
      return List.of(new SingleLazyInstanceSyntaxHighlighterFactory() {
        @Override
        protected @NotNull SyntaxHighlighter createHighlighter() {
          return highlighter;
        }
      });
    }
    return fromEp;
  }

  private synchronized void checkAddEPListener() {
    if (myEpListenerAdded) {
      return;
    }

    myEpListenerAdded = true;

    LanguageSyntaxHighlighters.EP_NAME.addExtensionPointListener(new MyEPListener(), null);
  }

  private final class MyEPListener implements ExtensionPointListener<KeyedLazyInstance<SyntaxHighlighter>>, ExtensionPointPriorityListener {
    @Override
    public void extensionAdded(@NotNull KeyedLazyInstance<SyntaxHighlighter> extension, @NotNull PluginDescriptor pluginDescriptor) {
      invalidateCacheForExtension(extension.getKey());
    }

    @Override
    public void extensionRemoved(@NotNull KeyedLazyInstance<SyntaxHighlighter> extension, @NotNull PluginDescriptor pluginDescriptor) {
      invalidateCacheForExtension(extension.getKey());
    }
  }
}
