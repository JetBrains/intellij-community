// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion.ml;

import com.intellij.openapi.util.UserDataHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Container for all factors and pre-computed data from {@link CompletionEnvironment} calculated inside {@link ContextFeatureProvider}.
 * Data inside the container can be reused by any {@link ElementFeatureProvider} to avoid code duplication and repeating of
 * expensive computations.
 * <p>
 * See the example below (some API is non-existent, but names are self-explained).
 * <pre> {@code
 *  class ExampleProvider implements ElementFeatureProvider {
 *
 *   ...
 *
 *   @Override
 *   public Map<@NonNls String, MLFeatureValue> calculateFeatures(...) {
 *     Map<@NonNls String, MLFeatureValue> result = new HashMap<>();
 *
 *     // reuse feature-values from ContextFeatureProvider
 *     if (Boolean.TRUE.equals(contextFeatures.binaryValue("ml_ctx_common_is_inside_if_expr"))) {
 *       result.put("boolean_type", CompletionUtil.isBool(element));
 *     }
 *
 *     // reuse user-data from ContextFeatureProvider
 *     Type expectedType = contextFeatures.getUserData(TypesContextFeaturesProvider.EXPECTED_TYPE_KEY);
 *     if (expectedType != null) {
 *       result.put("exact_type_match", expectedType.equals(CompletionUtil.getType(element)));
 *     }
 *
 *     return result;
 * }
 * } </pre>
 * <p>
 * See FAQ in {@link MLFeatureValue}
 *
 * @see ElementFeatureProvider
 * @see ContextFeatureProvider
 */
public interface ContextFeatures extends UserDataHolder {
  @Nullable
  Boolean binaryValue(@NotNull String name);

  @Nullable
  Double floatValue(@NotNull String name);

  @Nullable
  String categoricalValue(@NotNull String name);

  @Nullable
  String classNameValue(@NotNull String name);

  @NotNull
  Map<String, String> asMap();
}
