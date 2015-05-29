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
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

interface SmartPointerElementInfo {
  @Nullable
  Document getDocumentToSynchronize();

  void fastenBelt(int offset, @Nullable RangeMarker[] cachedRangeMarkers);

  void unfastenBelt(int offset);

  @Nullable
  PsiElement restoreElement();

  PsiFile restoreFile();

  int elementHashCode(); // must be immutable
  boolean pointsToTheSameElementAs(@NotNull SmartPointerElementInfo other);

  VirtualFile getVirtualFile();

  @Nullable
  Segment getRange();
  @NotNull Project getProject();

  void cleanup();
}
