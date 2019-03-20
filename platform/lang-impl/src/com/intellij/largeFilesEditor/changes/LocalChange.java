// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.largeFilesEditor.changes;

import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.util.CompressionUtil;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;

public class LocalChange implements Change {
  private final static int MAX_SHOWING_STRING_LENGTH = 10;

  private final long myPageNumber;
  private final int myOffset;
  private final Object myOldString;
  private final Object myNewString;
  private final long myTimeStamp;

  LocalChange(long pageNumber, DocumentEvent e) {
    this(pageNumber, e.getOffset(), e.getOldFragment(), e.getNewFragment(), e.getDocument().getModificationStamp());
  }

  LocalChange(long pageNumber,
              int offset,
              @NotNull CharSequence oldString,
              @NotNull CharSequence newString,
              long timeStamp) {
    myPageNumber = pageNumber;
    myOffset = offset;
    myOldString = CompressionUtil.compressStringRawBytes(oldString);
    myNewString = CompressionUtil.compressStringRawBytes(newString);
    myTimeStamp = timeStamp;
  }

  public long getPageNumber() {
    return myPageNumber;
  }

  public int getOffset() {
    return myOffset;
  }

  public CharSequence getOldString() {
    return CompressionUtil.uncompressStringRawBytes(myOldString);
  }

  public CharSequence getNewString() {
    return CompressionUtil.uncompressStringRawBytes(myNewString);
  }

  @Override
  public long getTimeStamp() {
    return myTimeStamp;
  }

  @Override
  public void performUndo(StringBuilder text) {
    CharSequence oldString = CompressionUtil.uncompressStringRawBytes(myOldString);
    CharSequence newString = CompressionUtil.uncompressStringRawBytes(myNewString);
    Utils.exchangeStringsInText(myOffset, newString.toString(), oldString.toString(), text);
  }

  @CalledInAwt
  @Override
  public void performUndo(DocumentEx document) {
    CharSequence oldString = CompressionUtil.uncompressStringRawBytes(myOldString);
    CharSequence newString = CompressionUtil.uncompressStringRawBytes(myNewString);
    Utils.exchangeStringsInDocument(myOffset, newString, oldString, document);
  }

  @Override
  public void performRedo(StringBuilder text) {
    CharSequence oldString = CompressionUtil.uncompressStringRawBytes(myOldString);
    CharSequence newString = CompressionUtil.uncompressStringRawBytes(myNewString);
    Utils.exchangeStringsInText(myOffset, oldString.toString(), newString.toString(), text);
  }

  @CalledInAwt
  @Override
  public void performRedo(DocumentEx document) {
    CharSequence oldString = CompressionUtil.uncompressStringRawBytes(myOldString);
    CharSequence newString = CompressionUtil.uncompressStringRawBytes(myNewString);
    Utils.exchangeStringsInDocument(myOffset, oldString, newString, document);
  }

  @Override
  public String toString() {
    CharSequence oldStrCharSeq = CompressionUtil.uncompressStringRawBytes(myOldString);
    CharSequence newStrCharSeq = CompressionUtil.uncompressStringRawBytes(myNewString);
    int oldStrLen = oldStrCharSeq.length();
    int newStrLen = newStrCharSeq.length();
    String oldStr = oldStrLen <= MAX_SHOWING_STRING_LENGTH ?
                    oldStrCharSeq.toString() :
                    oldStrCharSeq.subSequence(0, MAX_SHOWING_STRING_LENGTH).toString() + "...";
    String newStr = newStrLen <= MAX_SHOWING_STRING_LENGTH ?
                    newStrCharSeq.toString() :
                    newStrCharSeq.subSequence(0, MAX_SHOWING_STRING_LENGTH).toString() + "...";
    return String.format(
      "LocalChange: timeStamp=%d, offset=%d, oldStrLen=%d newStrLen=%d oldStr=\"%s\" newStr=\"%s\"",
      myTimeStamp,
      myOffset,
      oldStrLen,
      newStrLen,
      oldStr,
      newStr);
  }
}
