// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.TextChange;
import com.intellij.util.ObjectUtils;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Allows to store and retrieve {@link TextChange} objects assuming that they are applied to the same text.
 * <p/>
 * Provides ability to automatic merging them if necessary.
 * <p/>
 * Not thread-safe.
 */
@ApiStatus.Internal
public class TextChangesStorage {
  private final List<ChangeEntry> myChanges = new ArrayList<>();

  /**
   * @return    list of changes stored previously via {@link #store(TextChange)}. Note that the changes offsets relate to initial
   *            text and that returned list is sorted by start offset in ascending order
   * @see #store(TextChange)
   */
  public @NotNull List<TextChangeImpl> getChanges() {
    if (myChanges.isEmpty()) return Collections.emptyList();
    List<TextChangeImpl> result = new ArrayList<>(myChanges.size());

    for (ChangeEntry changeEntry : myChanges) {
      result.add(changeEntry.change);
    }
    return result;
  }

  /**
   * Allows to ask the storage for the list of changes that have intersections with the target text range (identified by the given
   * arguments).
   * 
   * @param start   target range start offset (inclusive)
   * @param end     target range end offset (exclusive)
   * @return        list that contains all registered changes that have intersections with the target text range
   */
  public @NotNull List<? extends TextChange> getChanges(int start, int end) {
    assert start <= end;
    
    int changeStartIndex = getChangeIndex(start);
    if (changeStartIndex < 0) {
      changeStartIndex = -changeStartIndex - 1;
    }
    if (changeStartIndex >= myChanges.size()) {
      return Collections.emptyList();
    }
    
    int changeEndIndex = getChangeIndex(end);
    boolean endInclusive = true;
    if (changeEndIndex < 0) {
      changeEndIndex = -changeEndIndex - 1;
      endInclusive = false;
    }

    List<TextChange> result = null;
    for (int i = changeStartIndex; i <= changeEndIndex; i++) {
      if (!endInclusive && i == changeEndIndex) {
        break;
      }
      if (result == null) {
        result = new ArrayList<>();
      }
      result.add(myChanges.get(i).change);
    }
    return result == null ? Collections.emptyList() : result;
  }
  
  public boolean isEmpty() {
    return myChanges.isEmpty();
  }
  
  public void clear() {
    myChanges.clear();
  }

  public int size() {
    return myChanges.size();
  }
  
  /**
   * Store given change merging it with previously stored ones if necessary.
   * <p/>
   * <b>Note:</b> it's assumed that given change offsets are related to the current state of the text ({@code 'client text'}),
   * i.e. with all stored changes applied to it. Example:
   * <ol>
   *   <li>Say, we have initial text {@code '12345'};</li>
   *   <li>
   *     Suppose the change {@code 'replace text at [2; 3) range with 'ABC''} is applied to it (stored at the current object).
   *     End-users see the text {@code '12ABC45'} now;
   *   </li>
   *   <li>
   *     This method is called with change like {@code 'replace text at [1; 6) range with 'XY''}. Change range is assumed to
   *     be related to the text visible to end-user, not initial one ({@code '12ABC45'}, not {@code '12345'}).
   *     I.e. the user will see text {@code '1XY5'} now;
   *   </li>
   * </ol>
   *
   * @param change    change to store
   */
  public void store(@NotNull TextChange change) {
    if (myChanges.isEmpty()) {
      myChanges.add(new ChangeEntry(new TextChangeImpl(change.getText(), change.getStart(), change.getEnd()), change.getStart()));
      return;
    }
    
    // There is a big chance that the document is processed sequentially from start to end, hence, it makes sense
    // to check if given change lays beyond other registered changes and register it quickly in case of success.
    ChangeEntry last = myChanges.get(myChanges.size() - 1);
    if (last.clientStartOffset + last.change.getText().length() < change.getStart()) {
      int clientShift = last.clientStartOffset - last.change.getStart() + last.change.getDiff();
      myChanges.add(new ChangeEntry(
        new TextChangeImpl(change.getText(), change.getStart() - clientShift, change.getEnd() - clientShift),
        change.getStart()
      ));
      return;
    }

    int insertionIndex = doStore(change);
    if (insertionIndex < 0) {
      return;
    }
    mergeIfNecessary(insertionIndex);
  }

  /**
   * Stores given change at the current storage and returns its index at {@link #myChanges changes collection} (if any).
   * 
   * @param change      change to store
   * @return            non-negative value that indicates index under which given change is stored at the
   *                    {@link #myChanges changes collection}; negative value if given change only modifies sub-range of
   *                    already registered range
   */
  @SuppressWarnings({"AssignmentToForLoopParameter"})
  private int doStore(@NotNull TextChange change) {
    int newChangeStart = change.getStart();
    int newChangeEnd = change.getEnd();
    int insertionIndex = getChangeIndex(change.getStart());
    int clientShift = 0; // 'Client text' shift before the given change to store. I.e. this value can be subtracted from the
                         // given change's start/end offsets in order to get original document range affected by the given change.
    int changeDiff = change.getText().length() - (change.getEnd() - change.getStart());

    if (insertionIndex < 0) {
      insertionIndex = -insertionIndex - 1;
      if (insertionIndex >= myChanges.size()) {
        if (!myChanges.isEmpty()) {
          ChangeEntry changeEntry = myChanges.get(myChanges.size() - 1);
          clientShift = changeEntry.clientStartOffset - changeEntry.change.getStart() + changeEntry.change.getDiff();
        }
        myChanges.add(new ChangeEntry(
          new TextChangeImpl(change.getText(), change.getStart() - clientShift, change.getEnd() - clientShift),
          change.getStart()
        ));
        return insertionIndex;
      }
      else if (insertionIndex > 0) {
        ChangeEntry changeEntry = myChanges.get(insertionIndex - 1);
        clientShift = changeEntry.clientStartOffset - changeEntry.change.getStart() + changeEntry.change.getDiff();
      }
    }
    else {
      ChangeEntry changeEntry = myChanges.get(insertionIndex);
      clientShift = changeEntry.clientStartOffset - changeEntry.change.getStart();
    }

    boolean updateClientOffsetOnly = false;
    for (int i = insertionIndex; i < myChanges.size(); i++) {
      ChangeEntry changeEntry = myChanges.get(i);
      int storedClientStart = changeEntry.change.getStart() + clientShift;
      CharSequence storedText = changeEntry.change.getText();
      int storedClientEnd = storedClientStart + storedText.length();

      // Stored change lays after the new one.
      if (!updateClientOffsetOnly && storedClientStart > newChangeEnd) {
        if (changeDiff != 0) {
          updateClientOffsetOnly = true;
        }
        else {
          break;
        }
      }

      if (updateClientOffsetOnly) {
        changeEntry.clientStartOffset += changeDiff;
        continue;
      }
      
      // Stored change lays before the new one.
      if (storedClientEnd <= newChangeStart) {
        clientShift += changeEntry.change.getDiff();
        insertionIndex = i + 1;
        continue;
      }
      
      // Check if given change targets sub-range of the stored one.
      if (storedClientStart <= newChangeStart && storedClientEnd >= newChangeEnd) {
        StringBuilder adjustedText = new StringBuilder();
        if (storedClientStart < newChangeStart) {
          adjustedText.append(storedText.subSequence(0, newChangeStart - storedClientStart));
        }
        adjustedText.append(change.getText());
        if (storedClientEnd > newChangeEnd) {
          adjustedText.append(storedText.subSequence(newChangeEnd - storedClientStart, storedText.length()));
        }

        if (adjustedText.length() == 0 && changeEntry.change.getStart() == changeEntry.change.getEnd()) {
          myChanges.remove(i);
          insertionIndex = -1;
          updateClientOffsetOnly = true;
          i--; // Assuming that 'i' is incremented at the 'for' loop.
          continue;
        }

        changeEntry.change = new TextChangeImpl(adjustedText, changeEntry.change.getStart(), changeEntry.change.getEnd());
        insertionIndex = -1;
        updateClientOffsetOnly = true;
        continue;
      }

      // Check if given change completely contains stored change range.
      if (newChangeStart <= storedClientStart && newChangeEnd >= storedClientEnd) {
        myChanges.remove(i);
        insertionIndex = i;
        newChangeEnd -= changeEntry.change.getDiff();
        i--; // Assuming that 'i' is incremented at the 'for' loop.
        continue;
      }

      // Check if given change intersects stored change range from the left.
      if (newChangeStart <= storedClientStart && newChangeEnd > storedClientStart) {
        int numberOfStoredChangeSymbolsToRemove = newChangeEnd - storedClientStart;
        CharSequence adjustedText = storedText.subSequence(numberOfStoredChangeSymbolsToRemove, storedText.length());
        changeEntry.change = new TextChangeImpl(adjustedText, changeEntry.change.getStart(), changeEntry.change.getEnd());
        changeEntry.clientStartOffset += changeDiff + numberOfStoredChangeSymbolsToRemove;
        newChangeEnd -= numberOfStoredChangeSymbolsToRemove;
        insertionIndex = i;
        continue;
      }

      // Check if given change intersects stored change range from the right.
      if (newChangeEnd >= storedClientEnd) {
        CharSequence adjustedText = storedText.subSequence(0, newChangeStart - storedClientStart);
        TextChangeImpl adjusted = new TextChangeImpl(adjustedText, changeEntry.change.getStart(), changeEntry.change.getEnd());
        changeEntry.change = adjusted;
        clientShift += adjusted.getDiff();
        newChangeEnd -= storedClientEnd - newChangeStart;
        insertionIndex = i + 1;
        continue;
      }

      // Check if given change is left-adjacent to the stored change.
      changeEntry.clientStartOffset += changeDiff;
    }

    if (insertionIndex >= 0) {
      myChanges.add(insertionIndex, new ChangeEntry(
        new TextChangeImpl(change.getText(), newChangeStart - clientShift, newChangeEnd - clientShift),
        change.getStart()
      ));
    }
    
    return insertionIndex;
  }

  /**
   * Merges if necessary change stored at {@link #myChanges changes collection} at the given index with adjacent changes.
   * 
   * @param insertionIndex      index of the change that can potentially be merged with adjacent changes
   */
  private void mergeIfNecessary(int insertionIndex) {
    // Merge with previous if necessary.
    ChangeEntry toMerge = myChanges.get(insertionIndex);
    if (insertionIndex > 0) {
      ChangeEntry left = myChanges.get(insertionIndex - 1);
      if (left.getClientEndOffset() == toMerge.clientStartOffset && left.change.getEnd() == toMerge.change.getStart()) {
        String text = left.change.getText().toString() + toMerge.change.getText();
        left.change = new TextChangeImpl(text, left.change.getStart(), toMerge.change.getEnd());
        myChanges.remove(insertionIndex);
        insertionIndex--;
      }
    }
    
    // Merge with next if necessary.
    toMerge = myChanges.get(insertionIndex);
    if (insertionIndex < myChanges.size() - 1) {
      ChangeEntry right = myChanges.get(insertionIndex + 1);
      if (toMerge.getClientEndOffset() == right.clientStartOffset && toMerge.change.getEnd() == right.change.getStart()) {
        String text = toMerge.change.getText().toString() + right.change.getText();
        toMerge.change = new TextChangeImpl(text, toMerge.change.getStart(), right.change.getEnd());
        myChanges.remove(insertionIndex + 1);
      }
    }
  }

  /**
   * Allows to retrieve character for the given index assuming that it should be resolved against 'client text', i.e. the text contained
   * at the given original char sequence with all {@link #myChanges registered changes} applied to it.
   * <p/>
   * Example:
   * <pre>
   * <ul>
   *   <li>Consider that original text is {@code '01234'};</li>
   *   <li>
   *     Consider that two changes are registered: {@code 'insert text 'a' at index 1'} and
   *     {@code 'insert text 'bc' at index 3'};
   *   </li>
   *   <li>{@code 'client text'} now is {@code '0a12bc34'};</li>
   *   <li>This method is called with index '5' - symbol 'c' is returned;</li>
   * </ul>
   * </pre>
   * 
   * @param originalData      original text to which {@link #myChanges registered changes} are applied
   * @param index             target symbol index (is assumed to be 'client text' index)
   * @return                  'client text' symbol at the given index
   */
  public char charAt(char @NotNull [] originalData, int index) {
    int changeIndex = getChangeIndex(index);
    if (changeIndex >= 0) {
      // Target char is contained at the stored change text
      ChangeEntry changeEntry = myChanges.get(changeIndex);
      if (changeEntry.change.getText().length() > index - changeEntry.clientStartOffset) {
        return changeEntry.change.getText().charAt(index - changeEntry.clientStartOffset);
      }
      else {
        int originalArrayIndex = index - (changeEntry.clientStartOffset - changeEntry.change.getStart() + changeEntry.change.getDiff());
        return originalData[originalArrayIndex];
      }
    }
    else {
      changeIndex = -changeIndex - 1;
      int clientShift = 0;
      if (changeIndex > 0 && changeIndex <= myChanges.size()) {
        ChangeEntry changeEntry = myChanges.get(changeIndex - 1);
        clientShift = changeEntry.clientStartOffset - changeEntry.change.getStart() + changeEntry.change.getDiff();
      }
      return originalData[index - clientShift];
    }
  }

  /**
   * Allows to build substring of the client text with its changes registered within the current storage.
   * 
   * @param originalData      original text to which {@link #myChanges registered changes} are applied
   * @param start             target substring start offset (against the 'client text'; inclusive)
   * @param end               target substring end offset (against the 'client text'; exclusive)
   * @return                  substring for the given text range
   */
  public CharSequence substring(char @NotNull [] originalData, int start, int end) {
    if (myChanges.isEmpty()) {
      return new String(originalData, start, end - start);
    }
    if (end == start) {
      return "";
    }
    
    int startChangeIndex = getChangeIndex(start);
    int endChangeIndex = getChangeIndex(end);
    
    boolean substringAffectedByChanges = startChangeIndex != endChangeIndex || startChangeIndex >= 0;
    int clientShift = 0;
    int originalStart = 0;
    if (startChangeIndex < 0) {
      startChangeIndex = -startChangeIndex - 1;
      if (startChangeIndex > 0 && startChangeIndex <= myChanges.size()) {
        ChangeEntry changeEntry = myChanges.get(startChangeIndex - 1);
        clientShift = changeEntry.clientStartOffset - changeEntry.change.getStart() + changeEntry.change.getDiff();
        originalStart = changeEntry.change.getEnd();
      }
    }
    else {
      ChangeEntry changeEntry = myChanges.get(startChangeIndex);
      clientShift = changeEntry.clientStartOffset - changeEntry.change.getStart();
    }
    
    if (!substringAffectedByChanges) {
      return new String(originalData, start - clientShift, end - start);
    }
    
    char[] data = new char[end - start];
    int outputOffset = 0;
    for (int i = startChangeIndex; i < myChanges.size() && outputOffset < data.length; i++) {
      ChangeEntry changeEntry = myChanges.get(i);
      int clientStart = changeEntry.clientStartOffset;
      if (clientStart >= end) {
        if (i == startChangeIndex) {
          return new String(originalData, start - clientShift, end - start);
        }
        System.arraycopy(originalData, originalStart, data, outputOffset, data.length - outputOffset);
        break;
      }
      int clientEnd = clientStart + changeEntry.change.getText().length();
      if (clientEnd > start) {
        if (clientStart > start) {
          int length = Math.min(clientStart - start, changeEntry.change.getStart() - originalStart);
          length = Math.min(length, data.length - outputOffset);
          System.arraycopy(originalData, changeEntry.change.getStart() - length, data, outputOffset, length);
          outputOffset += length;
          if (outputOffset >= data.length) {
            break;
          }
        }
        if (clientStart < clientEnd) {
          int changeTextStartOffset = start <= clientStart ? 0 : start - clientStart;
          int length = Math.min(clientEnd, end) - Math.max(clientStart, start);
          CharArrayUtil.getChars(changeEntry.change.getText(), data, changeTextStartOffset, outputOffset, length);
          outputOffset += length;
        }
      }
      originalStart = changeEntry.change.getEnd();
    }
    
    if (outputOffset < data.length) {
      System.arraycopy(originalData, originalStart, data, outputOffset, data.length - outputOffset);
    }
    return new String(data);
  }
  
  /**
   * Allows to find index of the change that contains given offset (assuming that it is used against {@code 'client text'})
   * or index of the first change that lays after the given offset.
   * 
   * @param clientOffset      target offset against the {@code 'client text'}
   * @return                  non-negative value that defines index of the stored change that contains given client offset;
   *                          negative value that indicates index of the first change that lays beyond the given offset and
   *                          is calculated by by {@code '-returned_index - 1'} formula
   */
  private int getChangeIndex(int clientOffset) {
    return ObjectUtils.binarySearch(0, myChanges.size(), i->{
      ChangeEntry changeEntry = myChanges.get(i);
      if (changeEntry.clientStartOffset > clientOffset) return 1;
      if (changeEntry.clientStartOffset + changeEntry.change.getText().length() < clientOffset) return -1;
      return 0;
    });
  }

  @Override
  public String toString() {
    return myChanges.toString();
  }

  /**
   * Utility class that contains target {@link TextChangeImpl document change} and auxiliary information associated with it.
   */
  private static class ChangeEntry {

    /** Target change. */
    public TextChangeImpl change;

    /**
     * Offset of the target change start at the 'client text'.
     */
    public int clientStartOffset;

    ChangeEntry(TextChangeImpl change, int clientStartOffset) {
      this.change = change;
      this.clientStartOffset = clientStartOffset;
    }

    /**
     * @return      end offset of the current change at the 'client text', i.e. {@link #clientStartOffset} plus change text length
     */
    public int getClientEndOffset() {
      return clientStartOffset + change.getText().length();
    }
    
    @Override
    public @NonNls String toString() {
      return "client start offset: " + clientStartOffset + ", change: " + change;
    }
  }
}
