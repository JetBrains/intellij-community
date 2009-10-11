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
package com.intellij.codeInsight.completion;

import com.intellij.util.ProcessingContext;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.featureStatistics.FeatureUsageTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Controls the text to display at the bottom of lookup list
 *
 * @author peter
 */
public abstract class CompletionAdvertiser {

  @Nullable public abstract String advertise(@NotNull CompletionParameters parameters, final ProcessingContext context);

  @Nullable public abstract String handleEmptyLookup(@NotNull CompletionParameters parameters, final ProcessingContext context);

  protected static String getShortcut(final String id) {
    return KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction(id));
  }

  protected static boolean shouldShowFeature(final CompletionParameters parameters, final String id) {
    return FeatureUsageTracker.getInstance().isToBeShown(id, parameters.getPosition().getProject());
  }

}