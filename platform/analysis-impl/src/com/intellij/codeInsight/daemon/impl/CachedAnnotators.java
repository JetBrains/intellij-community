// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageAnnotators;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public class CachedAnnotators {
  private final ThreadLocalAnnotatorMap<String, Annotator> cachedAnnotators = new ThreadLocalAnnotatorMap<String, Annotator>() {
    @NotNull
    @Override
    public Collection<Annotator> initialValue(@NotNull String languageId) {
      Language language = Language.findLanguageByID(languageId);
      return language == null ? ContainerUtil.emptyList() : LanguageAnnotators.INSTANCE.allForLanguageOrAny(language);
    }
  };

  public CachedAnnotators(Project project) {
    ExtensionPointListener<Annotator> listener = new ExtensionPointListener<Annotator>() {
      @Override
      public void extensionAdded(@NotNull Annotator extension, @Nullable PluginDescriptor pluginDescriptor) {
        cachedAnnotators.clear();
      }

      @Override
      public void extensionRemoved(@NotNull Annotator extension, @Nullable PluginDescriptor pluginDescriptor) {
        cachedAnnotators.clear();
      }
    };
    LanguageAnnotators.INSTANCE.addListener(listener, project);
  }

  @NotNull
  List<Annotator> get(@NotNull String languageId) {
    return cachedAnnotators.get(languageId);
  }
}
