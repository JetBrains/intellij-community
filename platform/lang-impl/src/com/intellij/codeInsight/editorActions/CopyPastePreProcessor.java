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
import com.intellij.openapi.editor.RawText;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface CopyPastePreProcessor {
  ExtensionPointName<CopyPastePreProcessor> EP_NAME = ExtensionPointName.create("com.intellij.copyPastePreProcessor");

  /**
   *
   * @param file
   * @param startOffsets
   * @param endOffsets
   * @param text
   * @return null if no preprocession is to be applied
   */
  @Nullable
  String preprocessOnCopy(final PsiFile file, final int[] startOffsets, final int[] endOffsets, String text);
  String preprocessOnPaste(final Project project, final PsiFile file, final Editor editor, String text, final RawText rawText);
}
