// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion.ml;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.openapi.util.UserDataHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Allows to get details where and how code completion is triggered.
 * Values added to {@link UserDataHolder} from {@link ContextFeatureProvider} may be accessed in {@link ElementFeatureProvider}
 * through user data of {@link ContextFeatures}.
 * <p>
 * See FAQ in {@link MLFeatureValue}
 */
public interface CompletionEnvironment extends UserDataHolder {
  @NotNull
  Lookup getLookup();

  @NotNull
  CompletionParameters getParameters();
}
