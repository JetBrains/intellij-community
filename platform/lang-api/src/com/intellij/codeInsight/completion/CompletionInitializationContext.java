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
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ReferenceRange;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author peter
 */
public class CompletionInitializationContext {
  public static final OffsetKey START_OFFSET = OffsetKey.create("startOffset", false);
  public static final OffsetKey SELECTION_END_OFFSET = OffsetKey.create("selectionEnd");
  public static final OffsetKey IDENTIFIER_END_OFFSET = OffsetKey.create("identifierEnd");

  /**
   * A default string that is inserted to the file before completion to guarantee that there'll always be some non-empty element there
   */
  public static @NonNls final String DUMMY_IDENTIFIER = "IntellijIdeaRulezzz ";
  public static @NonNls final String DUMMY_IDENTIFIER_TRIMMED = "IntellijIdeaRulezzz";
  private final Editor myEditor;
  private final PsiFile myFile;
  private final CompletionType myCompletionType;
  private final OffsetMap myOffsetMap;
  private FileCopyPatcher myFileCopyPatcher = new DummyIdentifierPatcher(DUMMY_IDENTIFIER);

  public CompletionInitializationContext(final Editor editor, final PsiFile file, final CompletionType completionType) {
    myEditor = editor;
    myFile = file;
    myCompletionType = completionType;
    myOffsetMap = new OffsetMap(editor.getDocument());

    final int caretOffset = editor.getCaretModel().getOffset();
    final SelectionModel selectionModel = editor.getSelectionModel();
    myOffsetMap.addOffset(START_OFFSET, selectionModel.hasSelection() ? selectionModel.getSelectionStart() : caretOffset);
    
    final int selectionEndOffset = selectionModel.hasSelection() ? selectionModel.getSelectionEnd() : caretOffset;
    myOffsetMap.addOffset(SELECTION_END_OFFSET, selectionEndOffset);
    myOffsetMap.addOffset(IDENTIFIER_END_OFFSET, findIdentifierEnd(file, selectionEndOffset));
  }

  private static int findIdentifierEnd(PsiFile file, int selectionEndOffset) {
    try {
      final PsiReference reference = file.findReferenceAt(selectionEndOffset);
      if(reference != null){
        final List<TextRange> ranges = ReferenceRange.getAbsoluteRanges(reference);
        for (TextRange range : ranges) {
          if (range.contains(selectionEndOffset)) {
            return range.getEndOffset();
          }
        }

        return reference.getElement().getTextRange().getStartOffset() + reference.getRangeInElement().getEndOffset();
      }
    }
    catch (IndexNotReadyException ignored) {
    }

    final String text = file.getText();
    int idEnd = selectionEndOffset;
    while (idEnd < text.length() && Character.isJavaIdentifierPart(text.charAt(idEnd))) {
      idEnd++;
    }
    return idEnd;
  }

  public void setFileCopyPatcher(@NotNull final FileCopyPatcher fileCopyPatcher) {
    myFileCopyPatcher = fileCopyPatcher;
  }

  @NotNull
  public FileCopyPatcher getFileCopyPatcher() {
    return myFileCopyPatcher;
  }

  @NotNull
  public Editor getEditor() {
    return myEditor;
  }

  @NotNull
  public CompletionType getCompletionType() {
    return myCompletionType;
  }

  @NotNull
  public Project getProject() {
    return myFile.getProject();
  }

  @NotNull
  public PsiFile getFile() {
    return myFile;
  }

  @NotNull
  public OffsetMap getOffsetMap() {
    return myOffsetMap;
  }

  public int getStartOffset() {
    return myOffsetMap.getOffset(START_OFFSET);
  }

  public int getSelectionEndOffset() {
    return myOffsetMap.getOffset(SELECTION_END_OFFSET);
  }

  public int getIdentifierEndOffset() {
    return myOffsetMap.getOffset(IDENTIFIER_END_OFFSET);
  }

}
