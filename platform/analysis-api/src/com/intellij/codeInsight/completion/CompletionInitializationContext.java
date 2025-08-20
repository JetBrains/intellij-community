// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class CompletionInitializationContext {
  public static final OffsetKey START_OFFSET = OffsetKey.create("startOffset", false);
  public static final OffsetKey SELECTION_END_OFFSET = OffsetKey.create("selectionEnd");
  public static final OffsetKey IDENTIFIER_END_OFFSET = OffsetKey.create("identifierEnd");

  /**
   * A default string that is inserted into the file before completion to guarantee that there'll always be some non-empty element there
   */
  public static final @NonNls String DUMMY_IDENTIFIER = CompletionUtilCore.DUMMY_IDENTIFIER;
  public static final @NonNls String DUMMY_IDENTIFIER_TRIMMED = CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED;
  private final @NotNull Editor myEditor;
  private final @NotNull Caret myCaret;
  private final @NotNull PsiFile myPsiFile;
  private final @NotNull CompletionType myCompletionType;
  private final int myInvocationCount;
  private final @NotNull OffsetMap myOffsetMap;
  private String myDummyIdentifier = DUMMY_IDENTIFIER;
  private final Language myPositionLanguage;

  public CompletionInitializationContext(@NotNull Editor editor,
                                         @NotNull Caret caret,
                                         Language language,
                                         @NotNull PsiFile psiFile,
                                         @NotNull CompletionType completionType,
                                         int invocationCount) {
    myEditor = editor;
    myCaret = caret;
    myPositionLanguage = language;
    myPsiFile = psiFile;
    myCompletionType = completionType;
    myInvocationCount = invocationCount;
    myOffsetMap = new OffsetMap(editor.getDocument());

    myOffsetMap.addOffset(START_OFFSET, calcStartOffset(caret));
    myOffsetMap.addOffset(SELECTION_END_OFFSET, calcSelectionEnd(caret));
    myOffsetMap.addOffset(IDENTIFIER_END_OFFSET, calcDefaultIdentifierEnd(editor, calcSelectionEnd(caret)));
  }

  @ApiStatus.Internal
  public static int calcSelectionEnd(Caret caret) {
    return caret.hasSelection() ? caret.getSelectionEnd() : caret.getOffset();
  }

  public static int calcStartOffset(Caret caret) {
    return caret.hasSelection() ? caret.getSelectionStart() : caret.getOffset();
  }

  public static int calcDefaultIdentifierEnd(Editor editor, int startFrom) {
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

  public @NotNull Language getPositionLanguage() {
    return Objects.requireNonNull(myPositionLanguage);
  }

  public String getDummyIdentifier() {
    return myDummyIdentifier;
  }

  public @NotNull Editor getEditor() {
    return myEditor;
  }

  public @NotNull Caret getCaret() {
    return myCaret;
  }

  public @NotNull CompletionType getCompletionType() {
    return myCompletionType;
  }

  public @NotNull Project getProject() {
    return myPsiFile.getProject();
  }

  public @NotNull PsiFile getFile() {
    return myPsiFile;
  }

  public @NotNull OffsetMap getOffsetMap() {
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
