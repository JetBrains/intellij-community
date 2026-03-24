// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.smartPointers;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.impl.FrozenDocument;
import com.intellij.openapi.editor.impl.ManualRangeMarker;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UnfairTextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A utility class responsible for caching and updating text ranges in response to document changes.
 * Is NOT synchronized.
 */
class MarkerCache {
  static final Comparator<SelfElementInfo> INFO_COMPARATOR = new SelfElementInfoComparator();
  private final SmartPointerTracker myPointers;

  /**
   * temporary cache of ranges that is recreated and dropped on each document commit.
   */
  private MarkerRanges myMarkerRanges;

  MarkerCache(@NotNull SmartPointerTracker pointers) {
    myPointers = pointers;
  }

  private @NotNull MarkerCache.MarkerRanges getUpdatedMarkers(@NotNull FrozenDocument frozen, @NotNull List<DocumentEventImpl> events) {
    int eventCount = events.size();
    assert eventCount > 0;

    MarkerRanges cache = myMarkerRanges;
    if (cache != null && cache.myEventCount == eventCount) return cache;

    MarkerRanges answer;
    if (cache != null && cache.myEventCount < eventCount) {
      // apply only the new events
      answer = cache.applyEvents(events.subList(cache.myEventCount, eventCount));
    }
    else {
      // cache is either missing or corresponds to a newer event (contains "future").
      // apply all events from scratch.
      List<SelfElementInfo> infos = myPointers.getSortedInfos();
      ManualRangeMarker[] markers = createMarkers(infos);
      MarkerRanges initial = new MarkerRanges(0, frozen, infos, markers);
      answer = initial.applyEvents(events);
    }

    myMarkerRanges = answer;
    return answer;
  }

  private static ManualRangeMarker @NotNull [] createMarkers(@NotNull List<? extends SelfElementInfo> infos) {
    ManualRangeMarker[] markers = new ManualRangeMarker[infos.size()];
    int i = 0;
    while (i < markers.length) {
      SelfElementInfo info = infos.get(i);
      boolean greedy = info.isGreedy();
      int start = info.getPsiStartOffset();
      int end = info.getPsiEndOffset();
      markers[i] = new ManualRangeMarker(start, end, greedy, greedy, !greedy, null);

      i++;
      while (i < markers.length && rangeEquals(infos.get(i), start, end, greedy)) {
        markers[i] = markers[i - 1];
        i++;
      }
    }
    return markers;
  }

  private static boolean rangeEquals(@NotNull SelfElementInfo info, int start, int end, boolean greedy) {
    return start == info.getPsiStartOffset() && end == info.getPsiEndOffset() && greedy == info.isGreedy();
  }

  boolean updateMarkers(@NotNull FrozenDocument frozen, @NotNull List<? extends DocumentEvent> events) {
    //noinspection unchecked
    MarkerRanges updated = getUpdatedMarkers(frozen, (List<DocumentEventImpl>)events);

    boolean sorted = true;
    for (int i = 0; i < updated.myMarkers.length; i++) {
      SelfElementInfo info = updated.mySortedInfos.get(i);
      info.setRange(updated.myMarkers[i]);
      if (sorted && i > 0 && INFO_COMPARATOR.compare(updated.mySortedInfos.get(i - 1), info) > 0) {
        sorted = false;
      }
    }

    myMarkerRanges = null;
    return sorted;
  }

  /**
   * infers the up-to-date range for the given segment in the given containingFile, and the frozen document after apllying the given events.
   */
  @Nullable
  TextRange getUpdatedRange(@NotNull SelfElementInfo info, @NotNull FrozenDocument frozen, @NotNull List<? extends DocumentEvent> events) {
    //noinspection unchecked
    MarkerRanges struct = getUpdatedMarkers(frozen, (List<DocumentEventImpl>)events);
    ManualRangeMarker updated = struct.findManualMarker(info);
    return updated == null ? null : new UnfairTextRange(updated.getStartOffset(), updated.getEndOffset());
  }

  /**
   * infers the up-to-date range for the given segment in the given containingFile, and the frozen document after apllying the given events.
   */
  static @Nullable Segment getUpdatedRange(@NotNull PsiFile containingFile,
                                           @NotNull Segment segment,
                                           boolean isSegmentGreedy,
                                           @NotNull FrozenDocument frozen,
                                           @NotNull List<? extends DocumentEvent> events) {
    MarkerRanges ranges = createInitialRangesForSegment(segment, isSegmentGreedy, frozen, events, containingFile);

    // NB: convert events from completion to whole doc change event to more precise translation
    //noinspection unchecked
    List<DocumentEventImpl> newEvents = ContainerUtil.map((List<DocumentEventImpl>)events, event -> {
      if (isWholeDocumentReplace(frozen, event)) {
        return withWholeTextReplaced(event);
      }
      else {
        return event;
      }
    });
    MarkerRanges updated = ranges.applyEvents(newEvents);
    return updated.myMarkers[0];
  }

  private static @NotNull MarkerRanges createInitialRangesForSegment(@NotNull Segment segment,
                                                                     boolean isSegmentGreedy,
                                                                     @NotNull FrozenDocument frozen,
                                                                     @NotNull List<? extends DocumentEvent> events,
                                                                     @NotNull PsiFile containingFile) {
    // using a mock SelfElementInfo to infer updated range for the segment
    SelfElementInfo info = new SelfElementInfo(ProperTextRange.create(segment), new MockIdentikit(), containingFile, isSegmentGreedy);
    List<SelfElementInfo> infos = Collections.singletonList(info);

    boolean greedy = info.isGreedy();
    int start = info.getPsiStartOffset();
    int end = info.getPsiEndOffset();
    boolean surviveOnExternalChange = ContainerUtil.exists(events, event -> isWholeDocumentReplace(frozen, (DocumentEventImpl)event));
    ManualRangeMarker marker = new ManualRangeMarker(start, end, greedy, greedy, surviveOnExternalChange, null);

    return new MarkerRanges(0, frozen, infos, new ManualRangeMarker[]{marker});
  }

  private static boolean isWholeDocumentReplace(@NotNull FrozenDocument frozen, @NotNull DocumentEventImpl event) {
    return event.getInitialStartOffset() == 0 && event.getInitialOldLength() == frozen.getTextLength();
  }

  void rangeChanged() {
    myMarkerRanges = null;
  }

  private static @NotNull DocumentEventImpl withWholeTextReplaced(@NotNull DocumentEventImpl event) {
    return new DocumentEventImpl(event.getDocument(),
                                 event.getOffset(),
                                 event.getOldFragment(),
                                 event.getNewFragment(),
                                 event.getOldTimeStamp(),
                                 true,
                                 event.getInitialStartOffset(),
                                 event.getInitialOldLength(),
                                 event.getMoveOffset());
  }

  private static @NotNull DocumentEventImpl withFrozenDocument(@NotNull DocumentEventImpl event, @NotNull FrozenDocument frozen) {
    return new DocumentEventImpl(frozen,
                                 event.getOffset(),
                                 event.getOldFragment(),
                                 event.getNewFragment(),
                                 event.getOldTimeStamp(),
                                 event.isWholeTextReplaced(),
                                 event.getInitialStartOffset(),
                                 event.getInitialOldLength(),
                                 event.getMoveOffset());
  }

  /**
   * An immutable cache of document state and marker ranges after applying a sequence of events.
   */
  private static class MarkerRanges {
    /**
     * Number of events that have been applied to the document to get this state.
     * Zero corresponds to the currently committed document.
     */
    private final int myEventCount;

    /**
     * The document state after applying {@link myEventCount} events.
     */
    private final FrozenDocument myResultDocument;

    /**
     * Sorted list of {@link SelfElementInfo} instances stored for this file.
     * Their state corresponds to the last committed document state, i.e., it is NOT in sync with {@link myResultDocument}.
     * The list is sorted by {@link SelfElementInfoComparator}.
     */
    private final List<SelfElementInfo> mySortedInfos;

    /**
     * Manual markers for {@link mySortedInfos}.
     * Their state is updated by applying {@link myEventCount} events.
     */
    private final ManualRangeMarker[] myMarkers;

    MarkerRanges(int eventCount,
                 @NotNull FrozenDocument resultDocument,
                 @NotNull List<SelfElementInfo> sortedInfos,
                 ManualRangeMarker @NotNull [] markers) {
      myEventCount = eventCount;
      myResultDocument = resultDocument;
      mySortedInfos = sortedInfos;
      myMarkers = markers;
    }

    /**
     * Applies the given events to the document state and updates the marker ranges.
     *
     * @return the updated cache after applying the given events.
     */
    @NotNull MarkerCache.MarkerRanges applyEvents(@NotNull List<DocumentEventImpl> events) {
      FrozenDocument frozen = myResultDocument;
      ManualRangeMarker[] resultMarkers = myMarkers.clone();
      for (DocumentEventImpl event : events) {
        FrozenDocument before = frozen;
        frozen = frozen.applyEvent(event, 0);
        DocumentEvent corrected = withFrozenDocument(event, frozen);

        int i = 0;
        while (i < resultMarkers.length) {
          ManualRangeMarker currentRange = resultMarkers[i];

          int sameMarkersEnd = i + 1;
          while (sameMarkersEnd < resultMarkers.length && resultMarkers[sameMarkersEnd] == currentRange) {
            sameMarkersEnd++;
          }

          ManualRangeMarker updatedRange = currentRange == null ? null : currentRange.getUpdatedRange(corrected, before);
          while (i < sameMarkersEnd) {
            resultMarkers[i] = updatedRange;
            i++;
          }
        }
      }
      return new MarkerRanges(myEventCount + events.size(), frozen, mySortedInfos, resultMarkers);
    }

    /**
     * Finds the manual marker for the given {@link SelfElementInfo}.
     */
    @Nullable ManualRangeMarker findManualMarker(@NotNull SelfElementInfo info) {
      int i = Collections.binarySearch(mySortedInfos, info, INFO_COMPARATOR);
      return i >= 0 ? myMarkers[i] : null;
    }
  }

  /**
   * Orders {@link SelfElementInfo} instances for deterministic range-marker processing:
   * <ol>
   *   <li>entries with a valid range (non-negative start offset) before entries without a range;</li>
   *   <li>ascending start offset;</li>
   *   <li>ascending end offset when starts are equal;</li>
   *   <li>non-greedy before greedy when ranges are identical.</li>
   * </ol>
   */
  private static class SelfElementInfoComparator implements Comparator<SelfElementInfo> {
    @Override
    public int compare(SelfElementInfo info1, SelfElementInfo info2) {
      int o1 = info1.getPsiStartOffset();
      int o2 = info2.getPsiStartOffset();
      if (o1 < 0 || o2 < 0) return o1 >= 0 ? -1 : o2 >= 0 ? 1 : 0; // infos without range go after infos with range
      if (o1 != o2) return o1 > o2 ? 1 : -1;

      o1 = info1.getPsiEndOffset();
      o2 = info2.getPsiEndOffset();
      if (o1 != o2) return o1 > o2 ? 1 : -1;

      return (info1.isGreedy() ? 1 : 0) - (info2.isGreedy() ? 1 : 0);
    }
  }

  private static class MockIdentikit extends Identikit {
    @Override
    public @Nullable PsiElement findPsiElement(@NotNull PsiFile file, int startOffset, int endOffset) {
      return null;
    }

    @Override
    public @NotNull Language getFileLanguage() {
      throw new IllegalStateException();
    }

    @Override
    public boolean isForPsiFile() {
      return false;
    }
  }
}
