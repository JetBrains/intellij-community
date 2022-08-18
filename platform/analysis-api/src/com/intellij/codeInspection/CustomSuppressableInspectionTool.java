/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.intellij.codeInspection;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This interface needs to be implemented by implementers of {@link LocalInspectionTool} and {@link GlobalInspectionTool}
 * that support suppressing inspections.
 *
 * @author max
 */
public interface CustomSuppressableInspectionTool {
  /**
   * Returns the list of suppression actions for the specified element.
   *
   * @param element the element on which Alt-Enter is pressed, or null if getting the list of available suppression actions in
   *                Inspections tool window
   * @return the array of suppression actions.
   */
  SuppressIntentionAction @Nullable [] getSuppressActions(@Nullable final PsiElement element);

  /**
   * Checks if the inspection is suppressed for the specified element.
   *
   * @param element the element to check
   * @return true if the inspection is suppressed, false otherwise.
   */
  boolean isSuppressedFor(@NotNull PsiElement element);
}
