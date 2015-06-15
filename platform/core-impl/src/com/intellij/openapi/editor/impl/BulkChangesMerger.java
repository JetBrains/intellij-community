/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.TextChange;
import com.intellij.util.text.StringFactory;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * Encapsulates logic of merging set of changes into particular text.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 12/22/10 12:02 PM
 */
@SuppressWarnings({"MethodMayBeStatic"})
public class BulkChangesMerger {

  public static final BulkChangesMerger INSTANCE = new BulkChangesMerger();
  private static final Logger LOG = Logger.getInstance("#" + BulkChangesMerger.class.getName());

  /**
   * Merges given changes within the given text and returns result as a new char sequence.
   *
   * @param text          text to apply given changes for
   * @param textLength    interested number of symbols from the given text to use
   * @param changes       changes to apply to the given text. It's assumed that there are no intersections between them and that they
   *                      are sorted by offsets in ascending order 
   * @return              merge result
   */
  public CharSequence mergeToCharSequence(@NotNull char[] text, int textLength, @NotNull List<? extends TextChange> changes) {
    return StringFactory.createShared(mergeToCharArray(text, textLength, changes));
  }
  
  /**
   * Merges given changes within the given text and returns result as a new char array.
   * 
   * @param text          text to apply given changes for
   * @param textLength    interested number of symbols from the given text to use
   * @param changes       changes to apply to the given text. It's assumed that there are no intersections between them and that they
   *                      are sorted by offsets in ascending order 
   * @return              merge result
   */
  public char[] mergeToCharArray(@NotNull char[] text, int textLength, @NotNull List<? extends TextChange> changes) {
    int newLength = textLength;
    for (TextChange change : changes) {
      newLength += change.getText().length() - (change.getEnd() - change.getStart());
    }
    char[] data = new char[newLength];
    int oldEndOffset = textLength;
    int newEndOffset = data.length;
    for (int i = changes.size() - 1; i >= 0; i--) {
      TextChange change = changes.get(i);
      
      // Copy all unprocessed symbols from initial text that lay after the changed offset.
      int symbolsToMoveNumber = oldEndOffset - change.getEnd();
      System.arraycopy(text, change.getEnd(), data, newEndOffset - symbolsToMoveNumber, symbolsToMoveNumber);
      newEndOffset -= symbolsToMoveNumber;
      
      // Copy all change symbols.
      char[] changeSymbols = change.getChars();
      newEndOffset -= changeSymbols.length;
      System.arraycopy(changeSymbols, 0, data, newEndOffset, changeSymbols.length);
      oldEndOffset = change.getStart();
    }
    
    if (oldEndOffset > 0) {
      System.arraycopy(text, 0, data, 0, oldEndOffset);
    }
    
    return data;
  }

  /**
   * Allows to perform 'in-place' merge of the given changes to the given array.
   * <p/>
   * I.e. it's considered that given array contains particular text at <code>[0; length)</code> region and given changes define
   * offsets against it. It's also assumed that given array length is enough to contain resulting text after applying the changes.
   * <p/>
   * Example: consider that initial text is <code>'12345'</code> and given changes are <code>'remove text at [1; 3) interval'</code>
   * and <code>'replace text at [4; 5) interval with 'abcde''</code>. Resulting text is <code>'14abcde'</code> then and given array
   * length should be not less than 7.
   * 
   * @param data      data array
   * @param length    initial text length (without changes)
   * @param changes   change to apply to the target text
   * @throws IllegalArgumentException     if given array is not big enough to contain the resulting text
   */
  public void mergeInPlace(@NotNull char[] data, int length, @NotNull List<? extends TextChangeImpl> changes)
    throws IllegalArgumentException
  {
    // Consider two corner cases:
    //     1. Every given change increases text length, i.e. change text length is more than changed region length. We can calculate
    //        resulting text length and start merging the changes from the right end then;
    //     2. Every given change reduces text length, start from the left end then;
    // The general idea is to group all of the given changes by 'add text'/ 'remove text' criteria and process them sequentially.
    // Example: let's assume we have the following changes:
    //     1) replace two symbols with five (diff +3);
    //     2) replace two symbols by one (diff -1);
    //     3) replace two symbols by one (diff -1);
    //     4) replace four symbols by one (diff -3);
    //     5) replace one symbol by two (diff +2);
    //     6) replace one symbol by three (diff +2);
    // Algorithm:
    //     1. Define the first group of change. First change diff is '+3', hence, iterate all changes until the resulting diff becomes
    //        less or equal to the zero. So, the first four changes conduct the first group. Initial change increased text length, hence,
    //        we process the changes from right to left starting at offset '4-th change start + 1';
    //     2. Current diff is '-2' (4-th change diff is '-3' and one slot was necessary for previous group completion), so, that means
    //        that we should process the 4-th and 5-th changes as the second group. Initial change direction is negative, hence, we
    //        process them from left to the right;
    //     3. Process the remaining change;
    if (changes.isEmpty()) {
      return;
    }
    
    int diff = 0;
    for (TextChangeImpl change : changes) {
      diff += change.getDiff();
    }
    if (length + diff > data.length) {
      throw new IllegalArgumentException(String.format(
        "Can't perform in-place changes merge. Reason: data array is not big enough to hold resulting text. Current size: %d, "
        + "minimum size: %d", data.length, length + diff
      ));
    }

    try {
      for (Context context = new Context(changes, data, length, length + diff); !context.isComplete();) {
        if (!context.startGroup()) {
          return;
        }
        context.endGroup();
      }
    }
    catch (RuntimeException e) {
      StringBuilder changesDescription = new StringBuilder();
      for (TextChangeImpl change : changes) {
        changesDescription.append(change.getText().length()).append(":").append(change.getStart()).append("-").append(change.getEnd())
          .append(",");
      }
      if (changesDescription.length() > 0) {
        changesDescription.setLength(changesDescription.length() - 1);
      } 
      LOG.error(String.format(
        "Invalid attempt to perform in-place document changes merge detected. Initial text length: %d, data array length: %d, "
        + "changes: [%s], changes diff: %d", length, data.length, changesDescription, diff
      ), e);
      char[] merged = mergeToCharArray(data, length, changes);
      System.arraycopy(merged, 0, data, 0, length + diff);
    }
  }
  
  private static void copy(@NotNull char[] data, int offset, @NotNull CharSequence text) {
    for (int i = 0; i < text.length(); i++) {
      data[i + offset] = text.charAt(i);
    }
  }

  /**
   * Given an offset of some location in the document, returns offset of this location after application of given changes. List of changes
   * is supposed to satisfy the same constraints as required by {@link #mergeToCharSequence(char[], int, List)} method.
   */
  public int updateOffset(int originalOffset, @NotNull List<? extends TextChange> changes) {
    int offset = originalOffset;
    for (TextChange change : changes) {
      if (originalOffset > change.getStart()) {
        offset += change.getText().length() - (change.getEnd() - change.getStart());
      }
    }
    return offset;
  } 
  
  private static class Context {

    private final List<? extends TextChangeImpl> myChanges;
    private final char[]                         myData;
    private final int                            myInputLength;
    private final int                            myOutputLength;
    private       int                            myDataStartOffset;
    private       int                            myDataEndOffset;
    private       int                            myChangeGroupStartIndex;
    private       int                            myChangeGroupEndIndex;
    private       int                            myDiff;
    private       int                            myFirstChangeShift;
    private       int                            myLastChangeShift;

    Context(@NotNull List<? extends TextChangeImpl> changes, @NotNull char[] data, int inputLength, int outputLength) {
      myChanges = changes;
      myData = data;
      myInputLength = inputLength;
      myOutputLength = outputLength;
    }

    /**
     * Asks current context to update its state in order to point to the first change in a group.
     * 
     * @return      <code>true</code> if the first change in a group is found; <code>false</code> otherwise
     */
    @SuppressWarnings({"ForLoopThatDoesntUseLoopVariable"})
    public boolean startGroup() {
      // Define first change that increases or reduces text length.
      for (boolean first = true; myDiff == 0 && myChangeGroupStartIndex < myChanges.size(); myChangeGroupStartIndex++, first = false) {
        TextChangeImpl change = myChanges.get(myChangeGroupStartIndex);
        myDiff = change.getDiff();
        if (first) {
          myDiff += myFirstChangeShift;
        }
        if (myDiff == 0) {
          copy(myData, change.getStart() + (first ? myFirstChangeShift : 0), change.getText());
        }
        else {
          myDataStartOffset = change.getStart();
          if (first) {
            myDataStartOffset += myFirstChangeShift;
          }
          break;
        }
      }
      return myDiff != 0;
    }
    
    public void endGroup() {
      boolean includeEndChange = false;
      myLastChangeShift = 0;
      for (myChangeGroupEndIndex = myChangeGroupStartIndex + 1; myChangeGroupEndIndex < myChanges.size(); myChangeGroupEndIndex++) {
        assert myDiff != 0 : String.format(
          "Text: '%s', length: %d, changes: %s, change group indices: %d-%d",
          Arrays.toString(myData), myInputLength, myChanges, myChangeGroupStartIndex, myChangeGroupEndIndex);
        TextChangeImpl change = myChanges.get(myChangeGroupEndIndex);
        int newDiff = myDiff + change.getDiff();

        // Changes group results to the zero text length shift.
        if (newDiff == 0) {
          myDataEndOffset = change.getEnd();
          includeEndChange = true;
          break;
        }

        // Changes group is not constructed yet.
        if (!(myDiff > 0 ^ newDiff > 0)) {
          myDiff = newDiff;
          continue;
        }

        // Current change finishes changes group.
        myDataEndOffset = change.getStart() + myDiff;
        myLastChangeShift = myDiff;
        break;
      }
      
      if (myChangeGroupEndIndex >= myChanges.size()) {
        if (myDiff > 0) {
          processLastPositiveGroup();
        }
        else {
          processLastNegativeGroup();
        }
        myChangeGroupStartIndex = myChangeGroupEndIndex = myChanges.size();
      }
      else if (myDiff > 0) {
        processPositiveGroup(includeEndChange);
      }
      else {
        processNegativeGroup(includeEndChange);
      }
      myDiff = 0;
      myChangeGroupStartIndex = myChangeGroupEndIndex;
      if (includeEndChange) {
        myChangeGroupStartIndex++;
      }
      myFirstChangeShift = myLastChangeShift;
    }
    
    /**
     * Asks to process changes group identified by [{@link #myChangeGroupStartIndex}; {@link #myChangeGroupEndIndex}) where
     * overall group direction is 'positive' (i.e. it starts from the change that increases text length).
     * 
     * @param includeEndChange    flag that defines if change defined by {@link #myChangeGroupEndIndex} should be processed
     */
    private void processPositiveGroup(boolean includeEndChange) {
      int outputOffset = myDataEndOffset;
      int prevChangeStart = -1;
      for (int i = myChangeGroupEndIndex; i >= myChangeGroupStartIndex; i--) {
        TextChangeImpl change = myChanges.get(i);
        if (prevChangeStart >= 0) {
          int length = prevChangeStart - change.getEnd();
          System.arraycopy(myData, change.getEnd(), myData, outputOffset - length, length);
          outputOffset -= length;
        }
        prevChangeStart = change.getStart();
        if (i == myChangeGroupEndIndex && !includeEndChange) {
          continue;
        }
        int length = change.getText().length();
        if (length > 0) {
          copy(myData, outputOffset - length, change.getText());
          outputOffset -= length;
        }
      }
    }

    private void processLastPositiveGroup() {
      int end = myChanges.get(myChanges.size() - 1).getEnd();
      int length = myInputLength - end;
      myDataEndOffset = myOutputLength - length;
      System.arraycopy(myData, end, myData, myDataEndOffset, length);
      myChangeGroupEndIndex = myChanges.size() - 1;
      processPositiveGroup(true);
    }
    
    private void processNegativeGroup(boolean includeEndChange) {
      int prevChangeEnd = -1;
      for (int i = myChangeGroupStartIndex; i <= myChangeGroupEndIndex; i++) {
        TextChangeImpl change = myChanges.get(i);
        if (prevChangeEnd >= 0) {
          int length = change.getStart() - prevChangeEnd;
          System.arraycopy(myData, prevChangeEnd, myData, myDataStartOffset, length);
          myDataStartOffset += length;
        }
        prevChangeEnd = change.getEnd();
        if (i == myChangeGroupEndIndex && !includeEndChange) {
          return;
        }
        int length = change.getText().length();
        if (length > 0) {
          copy(myData, myDataStartOffset, change.getText());
          myDataStartOffset += length;
        }
      }
    }

    private void processLastNegativeGroup() {
      myChangeGroupEndIndex = myChanges.size() - 1;
      processNegativeGroup(true);
      int end = myChanges.get(myChangeGroupEndIndex).getEnd();
      System.arraycopy(myData, end, myData, myDataStartOffset, myInputLength - end);
    }
    
    public boolean isComplete() {
      return myChangeGroupStartIndex >= myChanges.size();
    }
  }
}
