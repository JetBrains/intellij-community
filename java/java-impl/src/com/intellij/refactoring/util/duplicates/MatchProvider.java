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
package com.intellij.refactoring.util.duplicates;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author dsl
 */
public interface MatchProvider {
  /**
   * Call change signature here to avoid refactoring under write action
   * @param match match which requires signature to be changed e.g. due to stronger expected type, etc
   */
  void prepareSignature(Match match);

  PsiElement processMatch(Match match) throws IncorrectOperationException;

  List<Match> getDuplicates();

  /**
   * @return null if no confirmation prompt is expected
   */
  @Nullable Boolean hasDuplicates();

  @NlsContexts.Label
  @Nullable String getConfirmDuplicatePrompt(Match match);

  @NlsContexts.DialogTitle String getReplaceDuplicatesTitle(int idx, int size);
}
