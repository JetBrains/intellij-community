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
package com.intellij.application.options;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.TextChange;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.impl.TextChangeImpl;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregates, merges and exposes information about {@link Document document} changes.
 * <p/>
 * Intended usage scenario is to collect information about format preview document changes occurred during
 * tweaking format options and highlight corresponding sections.
 * <p/>
 * <b>Note:</b> current algorithm does consider changing particular text by the same text to be a document change, i.e. consider
 * the document like <code>'abcde'</code>. Suppose it's text from range <code>[2; 4)</code> (<code>'cd'</code>) is replaced by the same
 * text. It's still to be considered as a change.
 * <p/>
 * Not thread-safe.
 *
 * @author Denis Zhdanov
 * @since 10/11/10 5:10 PM
 */
public class DocumentChangesCollector implements DocumentListener {

  /**
   * Holds merged document changes.
   * <p/>
   * Is assumed to be always sorted by change start offset in ascending order.
   */
  private final List<TextChangeImpl> myChanges = new ArrayList<TextChangeImpl>();

  private boolean myCollectChanges;

  @Override
  public void beforeDocumentChange(DocumentEvent event) {
  }

  @Override
  public void documentChanged(DocumentEvent event) {
    if (!myCollectChanges) {
      return;
    }

    // The general algorithm is as follows:
    //   1. Drop or cut stored change ranges for removed range if any;
    //   2. Update offsets of all ranges that lays after the last change range identified by the given document change event;
    //   3. Merge added range with registered changes if any;
    //   4. Merge all adjacent ranges if any;
    StringBuilder oldText = new StringBuilder(event.getOldFragment());

    cutChangesIfNecessary(event, oldText);
    updateChanges(event, oldText);
    mergeChangesIfNecessary(event);
  }

  /**
   * Cuts or removes stored change ranges for the given interval if any.
   *
   * @param event     event for just occurred document change
   * @param oldText   our main idea is to merge all changes in order to have aggregated change against original document. So, there is
   *                  a possible case that subsequent change affects text inserted by the previous one. We don't want to reflect that
   *                  at aggregated change as it didn't appear in initial document. Hence, we have a mutable symbols buffer that holds
   *                  symbols from initial document affected by the given change
   */
  private void cutChangesIfNecessary(DocumentEvent event, StringBuilder oldText) {
    if (myChanges.isEmpty()) {
      return;
    }

    int start = event.getOffset();
    int end = event.getOffset() + event.getOldLength();
    int diff = event.getNewLength() - event.getOldLength();

    int forwardIndex = findIndex(start);
    int backwardIndexStart = forwardIndex - 1;
    if (forwardIndex < 0) {
      backwardIndexStart = myChanges.size() - 1;
    }
    else {
      TIntArrayList indices = new TIntArrayList();
      for (; forwardIndex < myChanges.size(); forwardIndex++) {
        TextChangeImpl change = myChanges.get(forwardIndex);
        // Stored change is not affected by the current change.
        if (change.getStart() >= end) {
          change.advance(diff);
        }

        // Stored change region is completely covered by the given change.
        else if (change.getEnd() <= end) {
          indices.add(forwardIndex);

          int deleteStart = start - change.getStart();
          deleteStart = Math.max(0, deleteStart);

          int deleteEnd = change.getEnd() - change.getStart();
          deleteEnd = Math.min(oldText.length(), Math.max(0, deleteEnd));

          oldText.delete(deleteStart, deleteEnd);
          oldText.insert(0, change.getText());
        }

        // Stored change region's start is covered by the current change.
        else {
          int deleteStart = change.getStart() - start;
          deleteStart = Math.min(oldText.length(), Math.max(0, deleteStart));

          int deleteEnd = oldText.length();
          deleteEnd = Math.min(oldText.length(), Math.max(0, deleteEnd));

          oldText.delete(deleteStart, deleteEnd);
          myChanges.set(forwardIndex, new TextChangeImpl(change.getText(), end + diff, change.getEnd() + diff));
        }
      }

      if (!indices.isEmpty()) {
        for (int i = indices.size() - 1; i >= 0; i--) {
          myChanges.remove(indices.get(i));
        }
      }
    }

    for (int i = Math.min(backwardIndexStart, myChanges.size() - 1); i >= 0; i--) {
      TextChangeImpl change = myChanges.get(i);
      if (change.getEnd() <= start) {
        break;
      }

      CharSequence textToUse = change.getText();
      int symbolsToCut = Math.min(change.getEnd(), end) - start;
      if (textToUse.length() >= symbolsToCut) {
        oldText.insert(symbolsToCut, textToUse.subSequence(textToUse.length() - symbolsToCut, textToUse.length()));
        textToUse = textToUse.subSequence(0, textToUse.length() - symbolsToCut);
      }
      oldText.delete(0, symbolsToCut);
      myChanges.set(i, new TextChangeImpl(textToUse, change.getStart(), start));
      if (change.getEnd() > end) {
        int shift = event.getOffset() + event.getNewLength() - end;
        TextChangeImpl changeTail = new TextChangeImpl("", end + shift, change.getEnd() + shift);
        if (i >= myChanges.size() - 1) {
          myChanges.add(changeTail);
        }
        else {
          myChanges.add(i + 1, changeTail);
        }
      }
    }
  }

  /**
   * Updates current object's state on the basis of the given event assuming that there are no stored change ranges that
   * start after offset denoted by the given event.
   *
   * @param event     event for just occurred document change
   * @param oldText   our main idea is to merge all changes in order to have aggregated change against original document. So, there is
   *                  a possible case that subsequent change affects text inserted by the previous one. We don't want to reflect that
   *                  at aggregated change as it didn't appear in initial document. Hence, we have a mutable symbols buffer that holds
   *                  symbols from initial document affected by the given change
   */
  private void updateChanges(DocumentEvent event, StringBuilder oldText) {
    int i = findIndex(event.getOffset());
    int endOffset = event.getOffset() + event.getNewLength();
    TextChangeImpl change = new TextChangeImpl(oldText, event.getOffset(), endOffset);
    if (i < 0) {
      myChanges.add(change);
    }
    else {
      myChanges.add(i, change);
    }
  }

  private void mergeChangesIfNecessary(DocumentEvent event) {
    // There is a possible case that we had more than one scattered change (e.g. (3; 5) and (8; 10)) and current document change affects
    // both of them (e.g. remove all symbols from offset (4; 9)). We have two changes then: (3; 4) and (4; 5) and want to merge them
    // into a single one.
    if (myChanges.size() < 2) {
      return;
    }
    TextChangeImpl next = myChanges.get(myChanges.size() - 1);
    for (int i = myChanges.size() - 2; i >= 0; i--) {
      TextChangeImpl current = myChanges.get(i);
      if (current.getEnd() < event.getOffset()) {
        // Assuming that change ranges are always kept at normalized form.
        break;
      }
      if (current.getEnd() == next.getStart()) {
        myChanges.set(i, next = new TextChangeImpl(current.getText().toString() + next.getText(), current.getStart(), next.getEnd()));
        myChanges.remove(i + 1);
      }
      else {
        next = current;
      }
    }
  }

  /**
   * Returns aggregated and merged document changes sorted by their start offsets in ascending order.
   * <p/>
   * <b>Example</b>
   * <pre>
   * <ol>
   *   <li>Suppose we have a document with text '0123456';</li>
   *   <li>
   *      Document range <code>[1; 3)</code> is removed (text <code>'12'</code> is removed, current text is
   *      <code>'03456'</code>);
   *   </li>
   *   <li>
   *      Document range <code>[1; 4)</code> (<code>'345'</code>) is replaced to <code>'abc'</code>
   *      (current text is <code>'0abc6'</code>);
   *   </li>
   * </ol>
   * </pre>
   * <p/>
   * So, initial document is <code>'0123456'</code>; final one is <code>'0abc6'</code>. We expect single aggregated change
   * to be returned then - <code>[1; 4)</code> with text <code>'12345'</code>, i.e. the range defines offset within the
   * current document and text shows initial document text at that range.
   *
   * @return    aggregated and merged document changes sorted by their start offsets in ascending order
   */
  @NotNull
  public List<? extends TextChange> getChanges() {
    return myChanges;
  }

  /**
   * Allows to switch <code>'collect document changes'</code> mode.
   * <p/>
   * Default value is <code>'false'</code>, i.e. document changes are not collected.
   *
   * @param collectChanges    new value of <code>'collect document changes'</code> mode
   */
  public void setCollectChanges(boolean collectChanges) {
    myCollectChanges = collectChanges;
  }

  /**
   * Forces current object to drop all information about document changes if any.
   */
  public void reset() {
    myChanges.clear();
  }

  /**
   * Finds index of the {@link #myCollectChanges first stored changed document ranges} which start offset is equal or greater to the
   * given one.
   *
   * @param offset    start offset of target document change
   * @return          non-negative value as an indication that there is not stored changed document range which start offset is equal
   *                  or greater than the given offset; negative value otherwise
   */
  private int findIndex(int offset) {
    if (myChanges.isEmpty()) {
      return -1;
    }

    // We assume that document is changed sequentially from start to end, hence, it's worth to perform quick offset comparison with
    // the last stored change if any
    TextChangeImpl change = myChanges.get(myChanges.size() - 1);
    if (offset > change.getStart()) {
      return -1;
    }

    int start = 0;
    int end = myChanges.size() - 1;
    int result = -1;

    // We inline binary search here mainly because TextChange class is immutable and we don't want unnecessary expenses on
    // new key object construction on every method call.
    while (start <= end) {
      result = (end + start) >>> 1;
      change = myChanges.get(result);
      if (change.getStart() < offset) {
        start = ++result;
        continue;
      }
      if (change.getStart() > offset) {
        end = result - 1;
        continue;
      }
      break;
    }

    return result;
  }
}
