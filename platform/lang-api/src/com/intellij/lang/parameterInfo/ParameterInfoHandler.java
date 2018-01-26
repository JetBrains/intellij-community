// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.intellij.lang.parameterInfo;

import com.intellij.codeInsight.lookup.LookupElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ParameterInfoHandler <ParameterOwner, ParameterType> {
  boolean couldShowInLookup();
  @Nullable Object[] getParametersForLookup(LookupElement item, ParameterInfoContext context);

  // Find element for parameter info should also set ItemsToShow in context and may set highlighted element
  @Nullable
  ParameterOwner findElementForParameterInfo(@NotNull CreateParameterInfoContext context);
  // Usually context.showHint
  void showParameterInfo(@NotNull final ParameterOwner element, @NotNull CreateParameterInfoContext context);

  // Null returns leads to removing hint
  @Nullable
  ParameterOwner findElementForUpdatingParameterInfo(@NotNull UpdateParameterInfoContext context);
  void updateParameterInfo(@NotNull final ParameterOwner parameterOwner, @NotNull UpdateParameterInfoContext context);

  // context.setEnabled / context.setupUIComponentPresentation
  void updateUI(ParameterType p, @NotNull ParameterInfoUIContext context);

  default boolean supportsOverloadSwitching() { return false; }
  default void dispose(@NotNull DeleteParameterInfoContext context) {}

  /** @deprecated not used */
  default @Nullable Object[] getParametersForDocumentation(ParameterType p, ParameterInfoContext context) { return null; }
  /** @deprecated not used */
  default @Nullable String getParameterCloseChars() { return null; }
  /** @deprecated not used */
  default boolean tracksParameterIndex() { return false; }
}
