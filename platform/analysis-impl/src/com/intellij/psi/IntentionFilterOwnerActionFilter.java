/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi;

import com.intellij.codeInsight.daemon.impl.IntentionActionFilter;
import com.intellij.codeInsight.intention.IntentionAction;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class IntentionFilterOwnerActionFilter implements IntentionActionFilter {
  @Override
  public boolean accept(@NotNull IntentionAction intentionAction, @Nullable PsiFile file) {
    if (!(file instanceof IntentionFilterOwner)) return true;

    final IntentionFilterOwner.IntentionActionsFilter actionsFilter = ((IntentionFilterOwner)file).getIntentionActionsFilter();
    if (actionsFilter == null || actionsFilter == IntentionFilterOwner.IntentionActionsFilter.EVERYTHING_AVAILABLE) return true;

    return actionsFilter.isAvailable(intentionAction);
  }
}
