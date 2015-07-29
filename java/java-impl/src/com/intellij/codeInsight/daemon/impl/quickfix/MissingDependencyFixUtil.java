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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.quickFix.MissingDependencyFixProvider;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.util.Function;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.codeInsight.daemon.quickFix.MissingDependencyFixProvider.EP_NAME;

/**
 * @author Vladislav.Soroka
 * @since 7/16/2015
 */
public class MissingDependencyFixUtil {
  @Nullable
  public static List<LocalQuickFix> findFixes(Function<MissingDependencyFixProvider, List<LocalQuickFix>> provider) {
    MissingDependencyFixProvider[] fixProviders = Extensions.getExtensions(EP_NAME);
    for (MissingDependencyFixProvider each : fixProviders) {
      List<LocalQuickFix> result = provider.fun(each);
      if (result != null && !result.isEmpty()) return result;
    }

    return null;
  }

  @Nullable
  public static <T> T provideFix(Function<MissingDependencyFixProvider, T> provider) {
    MissingDependencyFixProvider[] fixProviders = Extensions.getExtensions(EP_NAME);
    for (MissingDependencyFixProvider each : fixProviders) {
      T result = provider.fun(each);
      if (result != null && Boolean.FALSE != result) return result;
    }

    return null;
  }
}
