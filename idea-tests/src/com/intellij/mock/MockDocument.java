package com.intellij.mock;

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.impl.EmptyMarkupModel;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditReadOnlyListener;
import com.intellij.openapi.editor.ex.LineIterator;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.Disposable;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeListener;
import java.util.Map;

public class MockDocument implements DocumentEx {
  private Map myUserData = new HashMap();
  private StringBuffer myText = new StringBuffer();
  private long myModStamp = LocalTimeCounter.currentTime();

  public MockDocument() {
  }

  public MockDocument(String text) {
    myText.append(text);
  }

  public String getText() {
    return myText.toString();
  }

  public void replaceText(CharSequence chars, long newModificationStamp) {
    myText = new StringBuffer();
    myText.append(chars);
    myModStamp = newModificationStamp;
  }

  public int getListenersCount() {
    return 0;
  }

  public CharSequence textToCharArray() {
    return getText();
  }

  public char[] getChars() {
    return getText().toCharArray();
  }

  public CharSequence getCharsSequence() {
    return getText();
  }

  public int getTextLength() {
    return 0;
  }

  public int getLineCount() {
    return 0;
  }

  public int getLineNumber(int offset) {
    return 0;
  }

  public int getLineStartOffset(int line) {
    return 0;
  }

  public int getLineEndOffset(int line) {
    return 0;
  }

  public void insertString(int offset, CharSequence s) {
    myText.insert(offset, s);
  }

  public void deleteString(int startOffset, int endOffset) {
    myText.delete(startOffset, endOffset);
  }

  public void replaceString(int startOffset, int endOffset, CharSequence s) {
    myText.replace(startOffset, endOffset, s.toString());
    myModStamp = LocalTimeCounter.currentTime();
  }

  public boolean isWritable() {
    return false;
  }

  public long getModificationStamp() {
    return myModStamp;
  }

  public void fireReadOnlyModificationAttempt() {
  }

  public void addDocumentListener(DocumentListener listener) {
  }

  public void addDocumentListener(DocumentListener listener, Disposable parentDisposable) {
  }

  public void removeDocumentListener(DocumentListener listener) {
  }

  public RangeMarker createRangeMarker(int startOffset, int endOffset) {
    return null;
  }

  public RangeMarker createRangeMarker(int startOffset, int endOffset, boolean surviveOnExternalChange) {
    return null;
  }

  public MarkupModel getMarkupModel() {
    return null;
  }

  @NotNull
  public MarkupModel getMarkupModel(Project project) {
    return new EmptyMarkupModel(this);
  }

  public void addPropertyChangeListener(PropertyChangeListener listener) {
  }

  public void removePropertyChangeListener(PropertyChangeListener listener) {
  }

  public <T> T getUserData(Key<T> key) {
    return (T)myUserData.get(key);
  }

  public <T> void putUserData(Key<T> key, T value) {
    myUserData.put(key, value);
  }

  public void stripTrailingSpaces(boolean inChangedLinesOnly) {
  }

  public void setStripTrailingSpacesEnabled(boolean isEnabled) {
  }

  public int getLineSeparatorLength(int line) {
    return 0;
  }

  public LineIterator createLineIterator() {
    return null;
  }

  public void setModificationStamp(long modificationStamp) {
    myModStamp = modificationStamp;
  }

  public void setReadOnly(boolean isReadOnly) {
  }

  public RangeMarker getRangeGuard(int start, int end) {
    return null;
  }

  public void startGuardedBlockChecking() {
  }

  public void stopGuardedBlockChecking() {
  }

  public RangeMarker createGuardedBlock(int startOffset, int endOffset) {
    return null;
  }

  public void removeGuardedBlock(RangeMarker block) {
  }

  public RangeMarker getOffsetGuard(int offset) {
    return null;
  }

  public void addEditReadOnlyListener(EditReadOnlyListener listener) {
  }

  public void removeEditReadOnlyListener(EditReadOnlyListener listener) {
  }

  public void suppressGuardedExceptions() {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  public void unSuppressGuardedExceptions() {

  }

  public boolean isInEventsHandling() {
    return false;
  }

  public void setCyclicBufferSize(int bufferSize) {
  }

  public void setText(final CharSequence text) {
  }

  public RangeMarker createRangeMarker(final TextRange textRange) {
    return createRangeMarker(textRange.getStartOffset(), textRange.getEndOffset());
  }

}
