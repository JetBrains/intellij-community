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

package com.intellij.codeInsight.completion;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiFile;

public class CompletionContext {
  public static final Key<CompletionContext> COMPLETION_CONTEXT_KEY = Key.create("CompletionContext");

  public final Project project;
  public final Editor editor;
  public final PsiFile file;
  private final OffsetMap myOffsetMap;

  public CompletionContext(Project project, Editor editor, PsiFile file, final OffsetMap offsetMap){
    this.project = project;
    this.editor = editor;
    this.file = file;
    myOffsetMap = offsetMap;
  }

  public int getStartOffset() {
    return getOffsetMap().getOffset(CompletionInitializationContext.START_OFFSET);
  }

  public void setStartOffset(final int newStartOffset) {
    getOffsetMap().addOffset(CompletionInitializationContext.START_OFFSET, newStartOffset);
  }

  public int getSelectionEndOffset() {
    return getOffsetMap().getOffset(CompletionInitializationContext.SELECTION_END_OFFSET);
  }

  public void setSelectionEndOffset(final int selectionEndOffset) {
    getOffsetMap().addOffset(CompletionInitializationContext.SELECTION_END_OFFSET, selectionEndOffset);
  }

  public OffsetMap getOffsetMap() {
    return myOffsetMap;
  }
}

