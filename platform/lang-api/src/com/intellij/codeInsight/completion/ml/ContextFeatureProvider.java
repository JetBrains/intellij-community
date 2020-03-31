// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion.ml;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@ApiStatus.Internal
public interface ContextFeatureProvider {
  LanguageExtension<ContextFeatureProvider> EP_NAME = new LanguageExtension<>("com.intellij.completion.ml.contextFeatures");

  @NotNull
  static List<ContextFeatureProvider> forLanguage(@NotNull Language language) {
    return EP_NAME.allForLanguageOrAny(language);
  }

  @NotNull
  String getName();

  @NotNull
  default Map<String, MLFeatureValue> calculateFeatures(@NotNull Lookup lookup) {
    return Collections.emptyMap();
  }

  @NotNull
  default Map<String, MLFeatureValue> calculateFeatures(@NotNull CompletionEnvironment environment) {
    return calculateFeatures(environment.getLookup());
  }
}
