/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl;

import com.intellij.diagnostic.Dumpable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.text.CharArrayCharSequence;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.CharSequenceBackedByArray;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author cdr
 */
abstract class CharArray implements CharSequenceBackedByArray, Dumpable {
  private static final Logger LOG = Logger.getInstance("#" + CharArray.class.getName());

  @SuppressWarnings("UseOfArchaicSystemPropertyAccessors")
  private static final boolean DISABLE_DEFERRED_PROCESSING = Boolean.getBoolean("idea.document.deny.deferred.changes");

  @SuppressWarnings("UseOfArchaicSystemPropertyAccessors")
  private static final boolean DEBUG_DEFERRED_PROCESSING = LOG.isDebugEnabled() || Boolean.getBoolean("idea.document.debug.bulk.processing");
  /**
   * We can't exclude possibility of situation when <code>'defer changes'</code> state is {@link #setDeferredChangeMode(boolean) entered}
   * but not exited, hence, we want to perform automatic flushing if necessary in order to avoid memory leaks. This constant holds
   * a value that defines that 'automatic flushing' criteria, i.e. every time number of stored deferred changes exceeds this value,
   * they are automatically flushed.
   */
  private static final int MAX_DEFERRED_CHANGES_NUMBER = 10000;

  private final TextChangesStorage myDeferredChangesStorage;

  private volatile int myStart; // start offset in myArray (used as an optimization when call substring())
  private volatile int myCount;

  private volatile CharSequence myOriginalSequence;
  private volatile char[] myArray;
  private volatile Reference<String> myStringRef; // buffers String value - for not to generate it every time
  private volatile int myBufferSize;
  private volatile int myDeferredShift;
  private volatile boolean myDeferredChangeMode;
  private volatile boolean myHasDeferredChanges;
  // this lock is for mutual exclusion during read action access
  // (some fields are changed in read action too)
  private final Lock lock = new ReentrantLock();

  // We had a problems with bulk document text processing, hence, debug facilities were introduced. The fields group below work with them.
  // The main idea is to hold all history of bulk processing iteration in order to be able to retrieve it from client and reproduce the
  // problem.
  
  private final boolean myDebug = isDebug();

  boolean isDebug() {
    return DEBUG_DEFERRED_PROCESSING || DocumentImpl.CHECK_DOCUMENT_CONSISTENCY;
  }

  /**
   * Duplicate instance of the current char array that is used during debug processing as follows - apply every text change
   * from the bulk changes group to this instance immediately in order to be able to check if the current 'deferred change-aware'
   * instance functionally behaves at the same way as 'straightforward' one.
   */
  private CharArray myDebugArray;

  /**
   * Holds deferred changes create during the current bulk processing iteration.
   */
  private List<TextChangeImpl> myDebugDeferredChanges;

  /**
   * Document text on bulk processing start.
   */
  private String myDebugTextOnBatchUpdateStart;

  // bufferSize == 0 means unbounded
  CharArray(final int bufferSize, @NotNull char[] data, int length) {
    myBufferSize = bufferSize;
    myDeferredChangesStorage = new TextChangesStorage();
    myArray = Arrays.copyOf(data, length);
    myCount = length;

    if (myDebug) {
      myDebugArray = new CharArray(bufferSize, data, length) {
        @NotNull
        @Override
        protected DocumentEvent beforeChangedUpdate(int offset,
                                                    CharSequence oldString,
                                                    CharSequence newString,
                                                    boolean wholeTextReplaced) {
          return CharArray.this.beforeChangedUpdate(offset, oldString, newString, wholeTextReplaced);
        }

        @Override
        protected void afterChangedUpdate(@NotNull DocumentEvent event, long newModificationStamp) {
        }

        @Override
        protected void assertWriteAccess() {
        }

        @Override
        protected void assertReadAccess() {
        }

        @Override
        boolean isDebug() {
          return false;
        }
      };
      myDebugDeferredChanges = new ArrayList<TextChangeImpl>();
    }
    assertConsistency();
  }

  public void setBufferSize(int bufferSize) {
    assert bufferSize >= 0 : bufferSize;
    myBufferSize = bufferSize;
    assertConsistency();
  }

  private DocumentEvent startChange(int offset,
                                    @Nullable CharSequence oldString,
                                    @Nullable CharSequence newString,
                                    boolean wholeTextReplaced) {
    assert myStart == 0; // can't change substring
    assertWriteAccess();
    assertConsistency();

    return beforeChangedUpdate(offset, oldString, newString, wholeTextReplaced);
  }

  @NotNull
  protected abstract DocumentEvent beforeChangedUpdate(int offset,
                                                       @Nullable CharSequence oldString,
                                                       @Nullable CharSequence newString,
                                                       boolean wholeTextReplaced);
  protected abstract void afterChangedUpdate(@NotNull DocumentEvent event, long newModificationStamp);

  protected abstract void assertWriteAccess();
  protected abstract void assertReadAccess();

  private void setText(@NotNull CharSequence chars) {
    assertConsistency();
    myOriginalSequence = chars.toString();
    myArray = null;
    myStringRef = null;
    myCount = chars.length();
    assert myStart == 0; // can't change substring
    myDeferredChangesStorage.clear();
    myHasDeferredChanges = false;
    trimToSize();

    if (myDebug) {
      myDebugArray.setText(chars);
      myDebugDeferredChanges.clear();
    }
    assertConsistency();
  }

  private void assertConsistency() {
    if (isDeferredChangeMode()) {
      assert myOriginalSequence == null;
    }
    CharSequence originalSequence = myOriginalSequence;
    int origLen = originalSequence == null ? -1 : originalSequence.length();
    String string = myStringRef == null ? null : myStringRef.get();
    int stringLen = string == null ? -1 : string.length();
    assert origLen == stringLen || origLen==-1 || stringLen==-1;

    int count = myCount + myDeferredShift;
    assert count == origLen || origLen==-1;
    assert count == stringLen || stringLen==-1;

    if (!myDebug) return;
    final CharSequence seqFromCharArray;

    if (myArray != null) {
      assert myCount <= myArray.length;
      seqFromCharArray = new CharArrayCharSequence(myArray, myStart, myCount);
    }
    else {
      seqFromCharArray = null;
    }

    if (seqFromCharArray != null && originalSequence != null) {
      assert StringUtil.equals(seqFromCharArray, originalSequence);
    }
    if (!isDeferredChangeMode() && seqFromCharArray != null && string != null) {
      assert StringUtil.equals(seqFromCharArray, string);
    }
    if (originalSequence != null && string != null) {
      assert string.equals(originalSequence.toString());
    }

    myDebugArray.assertConsistency();

    CharSequence str = myStringRef == null ? null : myStringRef.get();
    if (str == null) {
      if (myHasDeferredChanges) {
        str = doSubString(0, myCount + myDeferredShift).toString();
      }
      else if (myOriginalSequence != null) {
        str = myOriginalSequence.toString();
      }
      else {
        str = seqFromCharArray;
      }
    }
    assert count == str.length();
    if (isDeferredChangeMode()) {
      String expected = myDebugArray.toString();
      checkStrings("toString()", expected, str);
    }
  }

  public void replace(int startOffset,
                      int endOffset,
                      @NotNull CharSequence toDelete,
                      @NotNull CharSequence newString,
                      long newModificationStamp,
                      boolean wholeTextReplaced) {
    final DocumentEvent event = startChange(startOffset, toDelete, newString, wholeTextReplaced);

    startOffset += myStart;
    endOffset += myStart;
    doReplace(startOffset, endOffset, newString);
    afterChangedUpdate(event, newModificationStamp);
    assertConsistency();
  }

  private void doReplace(int startOffset, int endOffset, @NotNull CharSequence newString) {
    prepareForModification();

    if (isDeferredChangeMode()) {
      storeChange(new TextChangeImpl(newString, startOffset, endOffset));
      if (myDebug) {
        myDebugArray.doReplace(startOffset, endOffset, newString);
      }
    }
    else {
      int newLength = newString.length();
      int oldLength = endOffset - startOffset;

      CharArrayUtil.getChars(newString, myArray, startOffset, Math.min(newLength, oldLength));
      myStringRef = null;

      if (newLength > oldLength) {
        doInsert(newString.subSequence(oldLength, newLength), endOffset);
      }
      else if (newLength < oldLength) {
        doRemove(startOffset + newLength, startOffset + oldLength);
      }
    }
  }

  public void remove(int startIndex, int endIndex, @NotNull CharSequence toDelete) {
    DocumentEvent event = startChange(startIndex, toDelete, null, false);
    startIndex += myStart;
    endIndex += myStart;
    doRemove(startIndex, endIndex);
    afterChangedUpdate(event, LocalTimeCounter.currentTime());
    assertConsistency();
  }

  private void doRemove(int startIndex, int endIndex) {
    if (startIndex == endIndex) {
      return;
    }
    prepareForModification();

    if (isDeferredChangeMode()) {
      storeChange(new TextChangeImpl("", startIndex, endIndex));
      if (myDebug) {
        myDebugArray.doRemove(startIndex, endIndex);
      }
    }
    else {
      if (endIndex < myCount) {
        System.arraycopy(myArray, endIndex, myArray, startIndex, myCount - endIndex);
        myStringRef = null;
      }
      myCount -= endIndex - startIndex;
    }
  }

  public void insert(@NotNull CharSequence s, int startIndex) {
    DocumentEvent event = startChange(startIndex, null, s, false);
    startIndex += myStart;
    doInsert(s, startIndex);

    afterChangedUpdate(event, LocalTimeCounter.currentTime());
    trimToSize();
    assertConsistency();
  }

  private void doInsert(@NotNull CharSequence s, final int startIndex) {
    prepareForModification();

    if (isDeferredChangeMode()) {
      storeChange(new TextChangeImpl(s, startIndex));
      if (myDebug) {
        myDebugArray.doInsert(s, startIndex);
      }
    }
    else {
      int insertLength = s.length();
      myArray = resizeArray(myArray, myCount + insertLength);
      if (startIndex < myCount) {
        System.arraycopy(myArray, startIndex, myArray, startIndex + insertLength, myCount - startIndex);
      }

      CharArrayUtil.getChars(s, myArray, startIndex);
      myCount += insertLength;
      myStringRef = null;
    }
  }

  /**
   * Stores given change at collection of deferred changes (merging it with others if necessary) and updates current object
   * state ({@link #length() length} etc).
   *
   * @param change      new change to store
   */
  private void storeChange(@NotNull TextChangeImpl change) {
    if (!change.isWithinBounds(length())) {
      LOG.error(
        "Invalid change attempt detected - given change bounds are not within the current char array. Change: " +
        change.getText().length()+":" + change.getStart()+"-" + change.getEnd(), dumpState());
      return;
    }
    if (myDeferredChangesStorage.size() >= MAX_DEFERRED_CHANGES_NUMBER) {
      flushDeferredChanged();
    }
    myDeferredChangesStorage.store(change);
    myHasDeferredChanges = true;
    myDeferredShift += change.getDiff();

    if (myDebug) {
      myDebugDeferredChanges.add(change);
    }
  }

  private void prepareForModification() {
    if (myOriginalSequence != null) {
      myArray = new char[myOriginalSequence.length()];
      CharArrayUtil.getChars(myOriginalSequence, myArray, 0);
      myCount = myArray.length;
      myOriginalSequence = null;
      myStart = 0;
    }
    myStringRef = null;

    assertConsistency();
  }

  @NotNull
  public CharSequence getCharArray() {
    assertConsistency();
    CharSequence originalSequence = myOriginalSequence;
    return originalSequence == null ? this : originalSequence;
  }

  @NotNull
  public String toString() {
    assertConsistency();
    String str = myStringRef == null ? null : myStringRef.get();
    if (str == null) {
      if (myHasDeferredChanges) {
        str = substring(0, length()).toString();
      }
      else {
        str = myOriginalSequence == null ? new String(myArray, myStart, myCount) : myOriginalSequence.toString();
      }
      myStringRef = new SoftReference<String>(str);
    }
    return str;
  }

  @Override
  public final int length() {
    final int result = myCount + myDeferredShift;
    if (myDebug && isDeferredChangeMode()) {
      int expected = myDebugArray.length();
      if (expected != result) {
        dumpDebugInfo("Incorrect length() processing. Expected: '" + expected + "', actual: '" + result + "'");
      }
    }
    return result;
  }

  @Override
  public final char charAt(int i) {
    if (i < 0 || i >= length()) {
      throw new IndexOutOfBoundsException("Wrong offset: " + i + "; count:" + length());
    }
    i += myStart;
    final char result;
    if (!myHasDeferredChanges) {
      if (myOriginalSequence != null) {
        result = myOriginalSequence.charAt(i);
      }
      else {
        result = myArray[i];
      }
    }
    else {
      result = myDeferredChangesStorage.charAt(myArray, i);
    }

    if (myDebug && isDeferredChangeMode()) {
      char expected = myDebugArray.charAt(i);
      if (expected != result) {
        dumpDebugInfo("Incorrect charAt() processing for index " + i + ". Expected: '" + expected + "', actual: '" + result + "'");
      }
    }
    return result;
  }

  @Override
  @NotNull
  public CharSequence subSequence(final int start, final int end) {
    assertReadAccess();
    assertConsistency();
    if (start == 0 && end == length()) return this;
    if (myOriginalSequence != null) {
      return myOriginalSequence.subSequence(start, end);
    }
    flushDeferredChanged();
    return new CharArrayCharSequence(myArray, start, end);
  }

  @Override
  @NotNull
  public char[] getChars() {
    assertReadAccess();
    assertConsistency();
    char[] array = myArray;
    CharSequence originalSequence = myOriginalSequence;
    if (myHasDeferredChanges || originalSequence != null && array == null) {
      // slow track
      lock.lock();
      try {
        flushDeferredChanged();
        if (myOriginalSequence != null && myArray == null) {
          myArray = array = ArrayUtil.realloc(CharArrayUtil.fromSequence(myOriginalSequence), myOriginalSequence.length());
          myStringRef = null;
        }
      }
      finally {
        lock.unlock();
      }
      assertConsistency();
    }
    return array;
  }

  @Override
  public void getChars(@NotNull final char[] dst, final int dstOffset) {
    assertReadAccess();
    assertConsistency();
    flushDeferredChanged();
    if (myOriginalSequence == null) {
      System.arraycopy(myArray, myStart, dst, dstOffset, length());
    }
    else {
      CharArrayUtil.getChars(myOriginalSequence, dst, dstOffset);
    }

    if (myDebug && isDeferredChangeMode()) {
      char[] expected = new char[dst.length];
      myDebugArray.getChars(expected, dstOffset);
      for (int i = dstOffset, j = myStart; i < dst.length && j < myArray.length; i++, j++) {
        if (expected[i] != myArray[j]) {
          dumpDebugInfo("getChars(char[], int). Given array of length " + dst.length + ", offset " + dstOffset + ". Found char '" + myArray[j] +
                        "' at index " + i + ", expected to find '" + expected[i] + "'");
          break;
        }
      }
    }
  }

  @NotNull
  public CharSequence substring(final int start, final int end) {
    assertReadAccess();
    final CharSequence result = doSubString(start, end);

    assertConsistency();
    return result;
  }

  private CharSequence doSubString(int start, int end) {
    if (start == end) return "";
    final CharSequence result;
    if (myOriginalSequence == null) {
      result = myDeferredChangesStorage.substring(myArray, start + myStart, end + myStart);
    }
    else {
      result = myOriginalSequence.subSequence(start, end);
    }
    return result;
  }

  @NotNull
  private static char[] resizeArray(@NotNull char[] array, int newSize) {
    if (newSize < array.length) {
      return array;
    }

    int newArraySize = array.length;
    if (newArraySize == 0) {
      newArraySize = 16;
    }
    while (newArraySize <= newSize) {
      newArraySize = newArraySize * 12 / 10 + 1;
    }
    char[] newArray = new char[newArraySize];
    System.arraycopy(array, 0, newArray, 0, array.length);
    return newArray;
  }

  private void trimToSize() {
    if (myBufferSize != 0 && length() > myBufferSize) {
      flushDeferredChanged();

      // make a copy
      int endIndex = myCount - myBufferSize;
      String toDelete = getCharArray().subSequence(0, endIndex).toString();
      remove(0, endIndex, toDelete);
    }
  }

  /**
   * @return    <code>true</code> if this object is in the defer changes mode, see {@link #setDeferredChangeMode(boolean)};
   */
  public boolean isDeferredChangeMode() {
    return myDeferredChangeMode;
  }

  public boolean hasDeferredChanges() {
    return myHasDeferredChanges;
  }
  
  /**
   * There is a possible case that client of this class wants to perform great number of modifications in a short amount of time
   * (e.g. end-user performs formatting of the document backed by the object of the current class). It may result in significant
   * performance degradation is the changes are performed one by one (every time the change is applied tail content is shifted to
   * the left or right). So, we may want to optimize that by avoiding actual array modification until information about
   * all target changes is provided and perform array data moves only after that.
   * <p/>
   * This method allows to define that <code>'defer changes'</code> mode usages, i.e. expected usage pattern is as follows:
   * <pre>
   * <ol>
   *   <li>
   *     Client of this class enters <code>'defer changes'</code> mode (calls this method with <code>'true'</code> argument).
   *     That means that all subsequent changes will not actually modify backed array data and will be stored separately;
   *   </li>
   *   <li>
   *     Number of target changes are applied to the current object via standard API
   *     ({@link #insert(CharSequence, int) insert},
   *     {@link #remove(int, int, CharSequence) remove} and
   *     {@link #replace(int, int, java.lang.CharSequence, java.lang.CharSequence, long, boolean)});
   *   </li>
   *   <li>
   *     Client of this class indicates that <code>'massive change time'</code> is over by calling this method with <code>'false'</code>
   *     argument. That flushes all deferred changes (if any) to the backed data array and makes every subsequent change to
   *     be immediate flushed to the backed array;
   *   </li>
   * </ol>
   * </pre>
   * <p/>
   * <b>Note:</b> we can't exclude possibility that <code>'defer changes'</code> mode is started but inadvertently not ended
   * (due to programming error, unexpected exception etc). Hence, this class is free to automatically end
   * <code>'defer changes'</code> mode when necessary in order to avoid memory leak with infinite deferred changes storing.
   * 
   * @param deferredChangeMode    flag that defines if <code>'defer changes'</code> mode should be used by the current object
   */
  public void setDeferredChangeMode(boolean deferredChangeMode) {
    if (!DISABLE_DEFERRED_PROCESSING) {
      if (deferredChangeMode) {
        if (myDebug) {
          myDebugArray.setText(myDebugTextOnBatchUpdateStart = toString());
          myDebugDeferredChanges.clear();
        }
        prepareForModification();
        myDeferredChangeMode = deferredChangeMode;
      }
      else {
        myDeferredChangeMode = deferredChangeMode;
        flushDeferredChanged();
      }
    }
    assertConsistency();
  }

  private void flushDeferredChanged() {
    List<TextChangeImpl> changes = myDeferredChangesStorage.getChanges();
    if (changes.isEmpty()) {
      return;
    }

    lock.lock();
    try {
      char[] beforeMerge = null;
      if (myDebug) {
        beforeMerge = new char[myArray.length];
        System.arraycopy(myArray, 0, beforeMerge, 0, myArray.length);
      }

      BulkChangesMerger changesMerger = BulkChangesMerger.INSTANCE;
      final boolean inPlace;
      if (myArray.length < length()) {
        myArray = changesMerger.mergeToCharArray(myArray, myCount, changes);
        inPlace = false;
      }
      else {
        changesMerger.mergeInPlace(myArray, myCount, changes);
        inPlace = true;
      }

      myCount += myDeferredShift;
      myDeferredShift = 0;
      myDeferredChangesStorage.clear();
      myHasDeferredChanges = false;
      myDeferredChangeMode = false;
      myStringRef = null;

      if (myDebug) {
        for (int i = 0, max = length(); i < max; i++) {
          if (myArray[i] != myDebugArray.myArray[i]) {
            dumpDebugInfo("flushDeferredChanged(). Index " + i + ", expected: '" + myDebugArray.myArray[i]+"', actual '" +
                          myArray[i]+"'. Text before merge: '" + Arrays.toString(beforeMerge)+"', merge inplace: "+inPlace);
            break;
          }
        }
      }
    }
    finally {
      lock.unlock();
    }
    assertConsistency();
  }

  @Override
  @NonNls
  @NotNull
  public String dumpState() {
    return "deferred changes mode: " + isDeferredChangeMode()+", length: " + length()+" (data array length: " + myCount+
           ", deferred shift: " + myDeferredShift+"); view offsets: [" + myStart+"; "+myCount+"]; deferred changes: "+myDeferredChangesStorage;
  }
  
  private void checkStrings(@NonNls @NotNull String operation, @NotNull String expected, @NotNull CharSequence actual) {
    if (StringUtil.equals(expected, actual)) {
      return;
    }
    for (int i = 0, max = Math.min(expected.length(), actual.length()); i < max; i++) {
      if (actual.charAt(i) != expected.charAt(i)) {
        dumpDebugInfo(
          "Incorrect " +
          operation+" processing. Expected length: " +
          expected.length()+", actual length: " +
          actual.length()+". Unmatched symbol at " +
          i+" - expected: '" +
          expected.charAt(i)+"', " +
          "actual: '" +
          actual.charAt(i)+"', expected document: '" +
          expected+"', actual document: '" +
          actual+"'"
        );
        return;
      }
    }
    dumpDebugInfo("Incorrect " + operation+" processing. Expected length: " + expected.length()+", actual length: " +
      actual.length()+", expected: '" + expected+"', actual: '" + actual+"'");
  }

  private void dumpDebugInfo(@NonNls @NotNull String problem) {
    LOG.error(
      "Incorrect CharArray processing detected: " + problem +
      ". Start: " + myStart
      + ", count: " + myCount + ", text on batch update start: " +
      myDebugTextOnBatchUpdateStart + ", deferred changes history: " +
      myDebugDeferredChanges + ", current deferred changes: " + myDeferredChangesStorage
    );
  }
}
