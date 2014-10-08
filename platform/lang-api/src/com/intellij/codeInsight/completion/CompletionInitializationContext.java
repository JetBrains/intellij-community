/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

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
  public static @NonNls final String DUMMY_IDENTIFIER = CompletionUtilCore.DUMMY_IDENTIFIER;
  public static @NonNls final String DUMMY_IDENTIFIER_TRIMMED = CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED;
  private final Editor myEditor;
  @NotNull
  private final Caret myCaret;
  private final PsiFile myFile;
  private final CompletionType myCompletionType;
  private final int myInvocationCount;
  private final OffsetMap myOffsetMap;
  private String myDummyIdentifier = DUMMY_IDENTIFIER;

  public CompletionInitializationContext(final Editor editor, final @NotNull Caret caret, final PsiFile file, final CompletionType completionType, int invocationCount) {
    myEditor = editor;
    myCaret = caret;
    myFile = file;
    myCompletionType = completionType;
    myInvocationCount = invocationCount;
    myOffsetMap = new OffsetMap(editor.getDocument());

    myOffsetMap.addOffset(START_OFFSET, calcStartOffset(caret));
    myOffsetMap.addOffset(SELECTION_END_OFFSET, calcSelectionEnd(caret));
    myOffsetMap.addOffset(IDENTIFIER_END_OFFSET, calcDefaultIdentifierEnd(editor, calcSelectionEnd(caret)));
  }

  private static int calcSelectionEnd(Caret caret) {
    return caret.hasSelection() ? caret.getSelectionEnd() : caret.getOffset();
  }

  public static int calcStartOffset(Caret caret) {
    return caret.hasSelection() ? caret.getSelectionStart() : caret.getOffset();
  }

  static int calcDefaultIdentifierEnd(Editor editor, int startFrom) {
    final CharSequence text = editor.getDocument().getCharsSequence();
    int idEnd = startFrom;
    while (idEnd < text.length() && Character.isJavaIdentifierPart(text.charAt(idEnd))) {
      idEnd++;
    }
    return idEnd;
  }

  public void setDummyIdentifier(@NotNull String dummyIdentifier) {
    myDummyIdentifier = dummyIdentifier;
  }

  @NotNull
  public Language getPositionLanguage() {
    return ObjectUtils.assertNotNull(PsiUtilBase.getLanguageInEditor(getEditor(), getProject()));
  }

  public String getDummyIdentifier() {
    return myDummyIdentifier;
  }

  @NotNull
  public Editor getEditor() {
    return myEditor;
  }

  @NotNull
  public Caret getCaret() {
    return myCaret;
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

  public int getReplacementOffset() {
    return getIdentifierEndOffset();
  }

  public int getInvocationCount() {
    return myInvocationCount;
  }

  /**
   * Mark the offset up to which the text will be deleted if a completion variant is selected using Replace character (Tab)
   */
  public void setReplacementOffset(int idEnd) {
    myOffsetMap.addOffset(IDENTIFIER_END_OFFSET, idEnd);
  }
}
