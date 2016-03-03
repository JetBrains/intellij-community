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
package com.intellij.core;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DocumentCommitProcessor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
* @author nik
*/
class MockDocumentCommitProcessor implements DocumentCommitProcessor {
  @Override
  public void commitSynchronously(@NotNull Document document, @NotNull Project project, @NotNull PsiFile psiFile) {
  }

  @Override
  public void commitAsynchronously(@NotNull Project project,
                                   @NotNull Document document,
                                   @NonNls @NotNull Object reason,
                                   @NotNull ModalityState currentModalityState) {
  }
}
