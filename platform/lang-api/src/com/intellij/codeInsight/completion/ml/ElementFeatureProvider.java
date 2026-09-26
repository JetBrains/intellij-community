// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.ml;

import com.intellij.codeInsight.completion.CompletionLocation;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Map;

/**
 * Computes element-specific factors that could be useful while reordering completion items. Newly added providers don't affect ordering
 * like {@link com.intellij.codeInsight.completion.CompletionWeigher} until ranking model that leverages new factors is trained.
 * <p>
 * See FAQ section in {@link MLFeatureValue}
 *
 * @see ContextFeatureProvider
 * @see com.intellij.codeInsight.completion.CompletionWeigher
 */
@ApiStatus.Internal
public interface ElementFeatureProvider {
  LanguageExtension<ElementFeatureProvider> EP_NAME = new LanguageExtension<>("com.intellij.completion.ml.elementFeatures");

  static @Unmodifiable @NotNull List<ElementFeatureProvider> forLanguage(Language language) {
    return EP_NAME.allForLanguageOrAny(language);
  }

  @NonNls String getName();

  /**
   * Invokes inside read action once per every item inside lookup.
   *
   * @param element         {@link LookupElement} to compute features for
   * @param location        describes where and how code completion is triggered
   * @param contextFeatures all features and pre-computed information given by all {@link ContextFeatureProvider}
   * @return container with element-features calculated
   */
  Map<@NonNls String, MLFeatureValue> calculateFeatures(@NotNull LookupElement element,
                                                        @NotNull CompletionLocation location,
                                                        @NotNull ContextFeatures contextFeatures);
}
