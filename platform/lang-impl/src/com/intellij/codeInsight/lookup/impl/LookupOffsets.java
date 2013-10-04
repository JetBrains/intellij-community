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

import com.intellij.codeInsight.completion.RangeMarkerSpy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.event.DocumentEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author peter
 */
public class LookupOffsets {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.lookup.impl.LookupOffsets");
  private String myAdditionalPrefix = "";
  private String myInitialPrefix;

  private boolean myStableStart;
  private RangeMarker myLookupStartMarker;
  private int myRemovedPrefix;
  private final RangeMarker myLookupOriginalStartMarker;
  private final Editor myEditor;

  public LookupOffsets(Editor editor) {
    myEditor = editor;
    int caret = getPivotOffset();
    myLookupOriginalStartMarker = editor.getDocument().createRangeMarker(caret, caret);
    myLookupOriginalStartMarker.setGreedyToLeft(true);
    updateLookupStart(0);
  }

  private void updateLookupStart(int minPrefixLength) {
    int offset = getPivotOffset();
    int start = offset - minPrefixLength - myAdditionalPrefix.length() + myRemovedPrefix;
    if (start < 0) {
      LOG.error("Invalid start offset: o=" + offset + ", mpl=" + minPrefixLength + ", ap=" + myAdditionalPrefix + ", rp=" + myRemovedPrefix);
      return;
    }
    if (myLookupStartMarker != null) {
      if (myLookupStartMarker.isValid() && myLookupStartMarker.getStartOffset() == start && myLookupStartMarker.getEndOffset() == start) {
        return;
      }
      myLookupStartMarker.dispose();
    }
    myLookupStartMarker = myEditor.getDocument().createRangeMarker(start, start);
    myLookupStartMarker.setGreedyToLeft(true);
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

    updateLookupStart(minPrefixLength);
  }

  int getLookupStart(String disposeTrace) {
    if (myLookupStartMarker == null) {
      LOG.error("disposed: " + disposeTrace);
    }
    if (!myLookupStartMarker.isValid()) {
      LOG.error("invalid marker: " + disposeTrace);
    }
    return myLookupStartMarker.getStartOffset();
  }

  int getLookupOriginalStart() {
    return myLookupOriginalStartMarker.isValid() ? myLookupOriginalStartMarker.getStartOffset() : -1;
  }

  boolean performGuardedChange(Runnable change, @Nullable final String debug) {
    if (myLookupStartMarker == null) {
      throw new AssertionError("null start before");
    }
    if (!myLookupStartMarker.isValid()) {
      throw new AssertionError("invalid start");
    }
    final Document document = myEditor.getDocument();
    RangeMarkerSpy spy = new RangeMarkerSpy(myLookupStartMarker) {
      @Override
      protected void invalidated(DocumentEvent e) {
        LOG.info("Lookup start marker invalidated, say thanks to the " + e +
                 ", doc=" + document +
                 ", debug=" + debug);
      }
    };
    document.addDocumentListener(spy);
    try {
      change.run();
    }
    finally {
      document.removeDocumentListener(spy);
    }
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

  void restorePrefix(int lookupStart) {
    if (myInitialPrefix != null) {
      myEditor.getDocument().replaceString(lookupStart, myEditor.getCaretModel().getOffset(), myInitialPrefix);
    }
  }

  public void disposeMarkers() {
    if (myLookupStartMarker != null) {
      myLookupStartMarker.dispose();
      myLookupStartMarker = null;
    }
    myLookupOriginalStartMarker.dispose();
  }

  public int getPrefixLength(LookupElement item, LookupImpl lookup) {
    return lookup.itemPattern(item).length() - myRemovedPrefix;
  }
}
