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

package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.Transferable;

/**
 * @author yole
 */
public interface CopyPastePostProcessor<T extends TextBlockTransferableData> {
  ExtensionPointName<CopyPastePostProcessor> EP_NAME = ExtensionPointName.create("com.intellij.copyPastePostProcessor");

  @Nullable
  T collectTransferableData(final PsiFile file, final Editor editor, final int[] startOffsets, final int[] endOffsets);

  @Nullable
  T extractTransferableData(final Transferable content);

  void processTransferableData(final Project project,
                               final Editor editor,
                               final RangeMarker bounds,
                               int caretOffset,
                               Ref<Boolean> indented,
                               final T value);
}
