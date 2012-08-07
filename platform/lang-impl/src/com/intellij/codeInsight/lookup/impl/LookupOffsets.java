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
  private RangeMarker myLookupOriginalStartMarker;
  private final Editor myEditor;

  public LookupOffsets(Editor editor) {
    myEditor = editor;
    int caret = editor.getCaretModel().getOffset();
    myLookupOriginalStartMarker = editor.getDocument().createRangeMarker(caret, caret);
    myLookupOriginalStartMarker.setGreedyToLeft(true);
    updateLookupStart(0);
  }

  int updateLookupStart(int myMinPrefixLength) {
    int offset = myEditor.getSelectionModel().hasSelection()
                 ? myEditor.getSelectionModel().getSelectionStart()
                 : myEditor.getCaretModel().getOffset();
    int start = Math.max(offset - myMinPrefixLength - myAdditionalPrefix.length(), 0);
    if (myLookupStartMarker != null) {
      if (myLookupStartMarker.isValid() && myLookupStartMarker.getStartOffset() == start && myLookupStartMarker.getEndOffset() == start) {
        return start;
      }
      myLookupStartMarker.dispose();
    }
    myLookupStartMarker = myEditor.getDocument().createRangeMarker(start, start);
    myLookupStartMarker.setGreedyToLeft(true);
    return start;
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
      minPrefixLength = Math.min(lookup.itemMatcher(item).getPrefix().length(), minPrefixLength);
    }

    updateLookupStart(minPrefixLength);
  }

  public int getLookupStart(String disposeTrace) {
    LOG.assertTrue(myLookupStartMarker.isValid(), disposeTrace);
    return myLookupStartMarker.getStartOffset();
  }

  public int getLookupOriginalStart() {
    return myLookupOriginalStartMarker.isValid() ? myLookupOriginalStartMarker.getStartOffset() : -1;
  }

  public boolean performGuardedChange(Runnable change, @Nullable final String debug) {
    assert myLookupStartMarker != null : "null start before";
    assert myLookupStartMarker.isValid() : "invalid start";
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


  public void setInitialPrefix(String presentPrefix, boolean explicitlyInvoked) {
    if (myAdditionalPrefix.length() == 0 && myInitialPrefix == null && !explicitlyInvoked) {
      myInitialPrefix = presentPrefix;
    }
    else {
      myInitialPrefix = null;
    }
  }

  public void clearAdditionalPrefix() {
    myAdditionalPrefix = "";
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
}
