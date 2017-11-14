/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class InsertionContext {
  public static final OffsetKey TAIL_OFFSET = OffsetKey.create("tailOffset", true);

  private final OffsetMap myOffsetMap;
  private final char myCompletionChar;
  private final LookupElement[] myElements;
  private final PsiFile myFile;
  private final Editor myEditor;
  private Runnable myLaterRunnable;
  private boolean myAddCompletionChar;

  public InsertionContext(final OffsetMap offsetMap, final char completionChar, final LookupElement[] elements,
                          @NotNull final PsiFile file,
                          @NotNull final Editor editor, final boolean addCompletionChar) {
    myOffsetMap = offsetMap;
    myCompletionChar = completionChar;
    myElements = elements;
    myFile = file;
    myEditor = editor;
    setTailOffset(editor.getCaretModel().getOffset());
    myAddCompletionChar = addCompletionChar;
  }

  public void setTailOffset(final int offset) {
    myOffsetMap.addOffset(TAIL_OFFSET, offset);
  }

  public int getTailOffset() {
    return myOffsetMap.getOffset(TAIL_OFFSET);
  }

  @NotNull
  public PsiFile getFile() {
    return myFile;
  }

  @NotNull
  public Editor getEditor() {
    return myEditor;
  }

  public void commitDocument() {
    PsiDocumentManager.getInstance(getProject()).commitDocument(getDocument());
  }

  @NotNull
  public Document getDocument() {
    return getEditor().getDocument();
  }

  public int getOffset(OffsetKey key) {
    return getOffsetMap().getOffset(key);
  }

  public OffsetMap getOffsetMap() {
    return myOffsetMap;
  }

  public OffsetKey trackOffset(int offset, boolean moveableToRight) {
    final OffsetKey key = OffsetKey.create("tracked", moveableToRight);
    getOffsetMap().addOffset(key, offset);
    return key;
  }

  public int getStartOffset() {
    return myOffsetMap.getOffset(CompletionInitializationContext.START_OFFSET);
  }

  public char getCompletionChar() {
    return myCompletionChar;
  }

  public LookupElement[] getElements() {
    return myElements;
  }

  public Project getProject() {
    return myFile.getProject();
  }

  public int getSelectionEndOffset() {
    return myOffsetMap.getOffset(CompletionInitializationContext.SELECTION_END_OFFSET);
  }

  @Nullable
  public Runnable getLaterRunnable() {
    return myLaterRunnable;
  }

  public void setLaterRunnable(@Nullable final Runnable laterRunnable) {
    myLaterRunnable = laterRunnable;
  }

  /**
   * Whether completionChar should be added to document at tail offset (see {@link #TAIL_OFFSET}) after insert handler.
   * By default this value is true (should be added).
   * @param addCompletionChar
   */
  public void setAddCompletionChar(final boolean addCompletionChar) {
    myAddCompletionChar = addCompletionChar;
  }

  public boolean shouldAddCompletionChar() {
    return myAddCompletionChar;
  }

  public CommonCodeStyleSettings getCodeStyleSettings() {
    Language lang = PsiUtilCore.getLanguageAtOffset(getFile(), getTailOffset());
    return CodeStyle.getLanguageSettings(getFile(), lang);
  }
}
