package com.intellij.openapi.diff.impl.processing;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;

public class Word {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.processing.Word");
  private final String myText;
  private final TextRange myRange;

  public Word(String text, TextRange range) {
    myText = text;
    myRange = range;
    LOG.assertTrue(myRange.getStartOffset() >= 0);
    LOG.assertTrue(myRange.getEndOffset() >= myRange.getStartOffset(), myRange);
  }

  public int hashCode() {
    return getText().hashCode();
  }

  public boolean equals(Object obj) {
    if (!(obj instanceof Word)) return false;
    Word other = (Word)obj;
    return getText().equals(other.getText());
  }

  public String getText() {
    return myText.substring(myRange.getStartOffset(), myRange.getEndOffset());
  }

  public String getPrefix(int fromPosition) {
    LOG.assertTrue(fromPosition >= 0, "" + fromPosition);
    int wordStart = myRange.getStartOffset();
    LOG.assertTrue(fromPosition <= wordStart, "" + fromPosition + " " + wordStart);
    return myText.substring(fromPosition, wordStart);
  }

  public int getEnd() {
    return myRange.getEndOffset();
  }

  public int getStart() {
    return myRange.getStartOffset();
  }

  public String toString() {
    return getText();
  }

  public boolean isWhitespace() {
    return false;
  }

  public boolean atEndOfLine() {
    int start = myRange.getStartOffset();
    if (start == 0) return true;
    if (myText.charAt(start - 1) == '\n') return true;
    int end = myRange.getEndOffset();
    if (end == myText.length()) return true;
    if (myText.charAt(end) == '\n') return true;
    return false;
  }
}
