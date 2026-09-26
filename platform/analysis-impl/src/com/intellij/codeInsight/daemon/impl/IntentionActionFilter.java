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

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * At least one of the {@code accept} methods must be implemented.
 */
public interface IntentionActionFilter {
  ExtensionPointName<IntentionActionFilter> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.daemon.intentionActionFilter");

  /**
   * @param psiFile - might (and will be) null. Return true in this case if you'd like to switch this kind of action in ANY file
   * @return true if the element must be accepted
   * Default implementation delegates to {@link #accept(IntentionAction, PsiFile, int)}
   */
  default boolean accept(@NotNull IntentionAction intentionAction, @Nullable PsiFile psiFile) {
    return accept(intentionAction, psiFile, -1);
  }
  
  /**
   * @param psiFile - might (and will be) null. Return true in this case if you'd like to switch this kind of action in ANY file
   * @param offset - offset within file where the intention is displayed. May be -1 if not known.
   * @return true if the element must be accepted
   * Default implementation delegates to {@link #accept(IntentionAction, PsiFile)}
   */
  default boolean accept(@NotNull IntentionAction intentionAction, @Nullable PsiFile psiFile, int offset) {
    return accept(intentionAction, psiFile);
  }
}

