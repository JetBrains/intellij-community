/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor;

import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;

import java.beans.PropertyChangeListener;

public interface Document extends UserDataHolder {
  String PROP_WRITABLE = "writable";
  /**
   * Retreives a copy of the document content. For obvious performance reasons use {@link #getCharsSequence()} whenever it's possible.
   * @return document content.
   */
  String getText();

  /**
   * Use this method instead of {@link #getText()} if you do not need to create a copy of the content.
   * Content represented by returned CharSequence is subject to change whenever document is modified via delete/replace/insertString method
   * calls. It is necessary to obtain Application.runWriteAction() to modify content of the document though so threading issues won't
   * arise.
   * @see #getTextLength()
   * @return inplace document content.
   */
  CharSequence getCharsSequence();

  /**
   * @deprecated Use {@link #getCharsSequence()} or {@link #getText()} instead.
   */
  char[] getChars();

  /**
   * @see #getCharsSequence()
   * @return size of the document's content.
   */
  int getTextLength();

  int getLineCount();
  int getLineNumber(int offset);
  int getLineStartOffset(int line);
  int getLineEndOffset(int line);

  /**
   * @throws ReadOnlyModificationException
   */
  void insertString(int offset, CharSequence s);

  /**
   * @throws ReadOnlyModificationException
   */
  void deleteString(int startOffset, int endOffset);

  /**
   * @throws ReadOnlyModificationException
   */
  void replaceString(int startOffset, int endOffset, CharSequence s);

  boolean isWritable();
  long getModificationStamp();
  void fireReadOnlyModificationAttempt();

  void addDocumentListener(DocumentListener listener);
  void removeDocumentListener(DocumentListener listener);

  RangeMarker createRangeMarker(int startOffset, int endOffset);
  RangeMarker createRangeMarker(int startOffset, int endOffset, boolean surviveOnExternalChange);

  /**
   * @deprecated
   */
  MarkupModel getMarkupModel();

  MarkupModel getMarkupModel(Project project);

  void addPropertyChangeListener(PropertyChangeListener listener);
  void removePropertyChangeListener(PropertyChangeListener listener);

  void setReadOnly(boolean isReadOnly);

  RangeMarker createGuardedBlock(int startOffset, int endOffset);
  void removeGuardedBlock(RangeMarker block);
  RangeMarker getOffsetGuard(int offset);
  RangeMarker getRangeGuard(int start, int end);

  void startGuardedBlockChecking();
  void stopGuardedBlockChecking();
}
