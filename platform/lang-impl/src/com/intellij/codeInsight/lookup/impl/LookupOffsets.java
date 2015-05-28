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
package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.psi.impl.DebugUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author peter
 */
public class LookupOffsets extends DocumentAdapter {
  private String myAdditionalPrefix = "";
  private String myInitialPrefix;

  private boolean myStableStart;
  private String myStartDisposeTrace;
  @NotNull private RangeMarker myLookupStartMarker;
  private int myRemovedPrefix;
  private final RangeMarker myLookupOriginalStartMarker;
  private final Editor myEditor;

  public LookupOffsets(Editor editor) {
    myEditor = editor;
    int caret = getPivotOffset();
    myLookupOriginalStartMarker = createLeftGreedyMarker(caret);
    myLookupStartMarker = createLeftGreedyMarker(caret);
    myEditor.getDocument().addDocumentListener(this);
  }

  @Override
  public void documentChanged(DocumentEvent e) {
    if (myStartDisposeTrace == null && !myLookupStartMarker.isValid()) {
      myStartDisposeTrace = e + "\n" + DebugUtil.currentStackTrace();
    }
  }

  private RangeMarker createLeftGreedyMarker(int start) {
    RangeMarker marker = myEditor.getDocument().createRangeMarker(start, start);
    marker.setGreedyToLeft(true);
    return marker;
  }

  private int getPivotOffset() {
    return myEditor.getSelectionModel().hasSelection()
                 ? myEditor.getSelectionModel().getSelectionStart()
                 : myEditor.getCaretModel().getOffset();
  }

  public String getAdditionalPrefix() {
    return myAdditionalPrefix;
  }

  public void appendPrefix(char c) {
    myAdditionalPrefix += c;
    myInitialPrefix = null;
  }

  public boolean truncatePrefix() {
    final int len = myAdditionalPrefix.length();
    if (len == 0) {
      myRemovedPrefix++;
      return false;
    }
    myAdditionalPrefix = myAdditionalPrefix.substring(0, len - 1);
    myInitialPrefix = null;
    return true;
  }

  void checkMinPrefixLengthChanges(Collection<LookupElement> items, LookupImpl lookup) {
    if (myStableStart) return;
    if (!lookup.isCalculating() && !items.isEmpty()) {
      myStableStart = true;
    }

    int minPrefixLength = items.isEmpty() ? 0 : Integer.MAX_VALUE;
    for (final LookupElement item : items) {
      if (!(item instanceof EmptyLookupItem)) {
        minPrefixLength = Math.min(lookup.itemMatcher(item).getPrefix().length(), minPrefixLength);
      }
    }

    int start = getPivotOffset() - minPrefixLength - myAdditionalPrefix.length() + myRemovedPrefix;
    start = Math.max(Math.min(start, myEditor.getDocument().getTextLength()), 0);
    if (myLookupStartMarker.isValid() && myLookupStartMarker.getStartOffset() == start && myLookupStartMarker.getEndOffset() == start) {
      return;
    }
    
    myLookupStartMarker.dispose();
    myLookupStartMarker = createLeftGreedyMarker(start);
    myStartDisposeTrace = null;
  }

  int getLookupStart(String disposeTrace) {
    if (!myLookupStartMarker.isValid()) {
      throw new AssertionError("Invalid lookup start: " + myLookupStartMarker + ", " + myEditor + ", disposeTrace=" + disposeTrace + ";\n" + myStartDisposeTrace);
    }
    return myLookupStartMarker.getStartOffset();
  }

  int getLookupOriginalStart() {
    return myLookupOriginalStartMarker.isValid() ? myLookupOriginalStartMarker.getStartOffset() : -1;
  }

  boolean performGuardedChange(Runnable change) {
    if (!myLookupStartMarker.isValid()) {
      throw new AssertionError("Invalid start: " + myEditor + ", trace=" + myStartDisposeTrace);
    }
    change.run();
    return myLookupStartMarker.isValid();
  }

  void setInitialPrefix(String presentPrefix, boolean explicitlyInvoked) {
    if (myAdditionalPrefix.length() == 0 && myInitialPrefix == null && !explicitlyInvoked) {
      myInitialPrefix = presentPrefix;
    }
    else {
      myInitialPrefix = null;
    }
  }

  void clearAdditionalPrefix() {
    myAdditionalPrefix = "";
    myRemovedPrefix = 0;
  }

  void restorePrefix() {
    if (myInitialPrefix == null || !myLookupStartMarker.isValid()) return;

    myEditor.getDocument().replaceString(myLookupStartMarker.getStartOffset(), myEditor.getCaretModel().getOffset(), myInitialPrefix);
  }

  void disposeMarkers() {
    myEditor.getDocument().removeDocumentListener(this);
    myLookupStartMarker.dispose();
    myLookupOriginalStartMarker.dispose();
  }

  public int getPrefixLength(LookupElement item, LookupImpl lookup) {
    return lookup.itemPattern(item).length() - myRemovedPrefix;
  }
}
