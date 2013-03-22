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
package com.intellij.psi;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A pointer to a PSI element which can survive PSI reparse.
 *
 * @see com.intellij.psi.SmartPointerManager#createSmartPsiElementPointer(PsiElement)
 */
public interface SmartPsiElementPointer<E extends PsiElement> {
  /**
   * Returns the PSI element corresponding to the one from which the smart pointer was created in the
   * current state of the PSI file.
   *
   * @return the PSI element, or null if the PSI reparse has completely invalidated the pointer (for example,
   * the element referenced by the pointer has been deleted).
   */
  @Nullable
  E getElement();

  @Nullable
  PsiFile getContainingFile();

  @NotNull
  Project getProject();

  VirtualFile getVirtualFile();

  @Nullable
  Segment getRange();
}
