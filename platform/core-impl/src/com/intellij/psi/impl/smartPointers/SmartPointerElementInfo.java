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

package com.intellij.psi.impl.smartPointers;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class SmartPointerElementInfo {
  @Nullable
  Document getDocumentToSynchronize() {
    return null;
  }

  void fastenBelt(@NotNull SmartPointerManagerImpl manager) {
  }

  @Nullable
  abstract PsiElement restoreElement(@NotNull SmartPointerManagerImpl manager);

  abstract PsiFile restoreFile(@NotNull SmartPointerManagerImpl manager);

  abstract int elementHashCode(); // must be immutable
  abstract boolean pointsToTheSameElementAs(@NotNull SmartPointerElementInfo other, @NotNull SmartPointerManagerImpl manager);

  abstract VirtualFile getVirtualFile();

  @Nullable
  abstract Segment getRange(@NotNull SmartPointerManagerImpl manager);

  void cleanup() {
  }

  @Nullable
  abstract Segment getPsiRange(@NotNull SmartPointerManagerImpl manager);
}
