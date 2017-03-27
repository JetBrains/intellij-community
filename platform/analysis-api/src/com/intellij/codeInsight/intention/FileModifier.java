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
package com.intellij.codeInsight.intention;

import com.intellij.openapi.application.WriteActionAware;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An interface that {@link IntentionAction} and {@link com.intellij.codeInspection.LocalQuickFix} share.
 *
 * @since 171.*
 * @author peter
 */
public interface FileModifier extends WriteActionAware {

  /**
   * Controls whether this intention/fix is going to modify the current file.
   * If {@code @NotNull}, and the current file is read-only,
   * it will be made writable (honoring version control integration) before the intention/fix is invoked. <p/>
   *
   * By default, as a heuristic, returns the same as {@link #startInWriteAction()}.<p/>
   *
   * If the action is going to modify multiple files, or the set of the files is unknown in advance, please
   * don't bother overriding this method, return {@code false} from {@link #startInWriteAction()}, and call {@link com.intellij.codeInsight.FileModificationService} methods in the implementation, and take write actions yourself as needed.
   *
   * @param currentFile the same file where intention would be invoked (for {@link com.intellij.codeInspection.LocalQuickFix} it would be the containing file of {@link com.intellij.codeInspection.ProblemDescriptor#getPsiElement})
   */
  @Nullable
  default PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
    return startInWriteAction() ? currentFile : null;
  }
}
