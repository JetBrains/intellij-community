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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class SmartPointerElementInfo {
  @Nullable
  public Document getDocumentToSynchronize() {
    return null;
  }

  public void fastenBelt() {
  }

  @Nullable
  public abstract PsiElement restoreElement();

  public abstract PsiFile restoreFile();

  public abstract int elementHashCode(); // must be immutable
  public abstract boolean pointsToTheSameElementAs(@NotNull SmartPointerElementInfo other);

  public abstract VirtualFile getVirtualFile();

  @Nullable
  public abstract Segment getRange();
  @NotNull
  public abstract Project getProject();

  public void cleanup() {
  }

  @Nullable
  public abstract Segment getPsiRange();
}
