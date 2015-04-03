/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.tools.util;

import com.intellij.diff.util.DiffDividerDrawUtil;
import com.intellij.diff.util.DiffDrawUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FoldingListener;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.util.BooleanGetter;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.HashSet;
import gnu.trove.TIntFunction;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class FoldingModelSupport {
  public static final String PLACEHOLDER = "     ";

  private static final Key<FoldingCache> CACHE_KEY = Key.create("Diff.FoldingUtil.Cache");

  protected final int myCount;
  @NotNull protected final EditorEx[] myEditors;

  @NotNull protected final List<FoldedBlock> myFoldings = new ArrayList<FoldedBlock>();

  private boolean myDuringSynchronize;
  private boolean myShouldUpdateLineNumbers;

  public FoldingModelSupport(@NotNull EditorEx[] editors, @NotNull Disposable disposable) {
    myEditors = editors;
    myCount = myEditors.length;

    if (myCount > 1) {
      for (int i = 0; i < myCount; i++) {
        myEditors[i].getFoldingModel().addListener(new MyFoldingListener(i), disposable);
        myEditors[i].getGutterComponentEx().setLineNumberConvertor(getLineConvertor(i));
      }
    }
  }

  //
  // Init
  //

  /*
   * Iterator returns ranges of changed lines: start1, end1, start2, end2, ...
   */
  protected void install(@Nullable final Iterator<int[]> changedLines,
                         @NotNull final UserDataHolder context,
                         final boolean defaultExpanded,
                         final int range) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    if (changedLines == null) return;
    if (range == -1) return;

    runBatchOperation(new Runnable() {
      @Override
      public void run() {
        ExpandSuggester expandSuggester = new ExpandSuggester(context.getUserData(CACHE_KEY), defaultExpanded);

        int[] lineCount = new int[myCount];
        for (int i = 0; i < myCount; i++) {
          lineCount[i] = myEditors[i].getDocument().getLineCount();
        }

        int[] starts = new int[myCount];
        int[] ends = new int[myCount];

        int[] last = new int[myCount];
        for (int i = 0; i < myCount; i++) {
          last[i] = Integer.MIN_VALUE;
        }

        while (changedLines.hasNext()) {
          int[] offsets = changedLines.next();

          for (int i = 0; i < myCount; i++) {
            starts[i] = bound(last[i] + range, 0, lineCount[i]);
            ends[i] = bound(offsets[i * 2] - range, 0, lineCount[i]);
          }
          addRange(starts, ends, expandSuggester.isExpanded(starts, ends));

          for (int i = 0; i < myCount; i++) {
            last[i] = offsets[i * 2 + 1];
          }
        }

        for (int i = 0; i < myCount; i++) {
          starts[i] = bound(last[i] + range, 0, lineCount[i]);
          ends[i] = bound(Integer.MAX_VALUE - range, 0, lineCount[i]);
        }
        addRange(starts, ends, expandSuggester.isExpanded(starts, ends));
      }
    });

    updateLineNumbers(true);
  }

  private void addRange(int[] starts, int[] ends, boolean expanded) {
    boolean hasFolding = false;
    FoldRegion[] regions = new FoldRegion[myCount];
    for (int i = 0; i < myCount; i++) {
      if (ends[i] - starts[i] < 2) continue;
      regions[i] = addFolding(myEditors[i], starts[i], ends[i], expanded);
      hasFolding |= regions[i] != null;
    }
    if (hasFolding) myFoldings.add(new FoldedBlock(regions));
  }

  @Nullable
  private static FoldRegion addFolding(@NotNull EditorEx editor, int start, int end, boolean expanded) {
    DocumentEx document = editor.getDocument();
    final int startOffset = document.getLineStartOffset(start);
    final int endOffset = document.getLineEndOffset(end - 1);

    FoldRegion value = editor.getFoldingModel().addFoldRegion(startOffset, endOffset, PLACEHOLDER);
    if (value != null) value.setExpanded(expanded);
    return value;
  }

  private void runBatchOperation(@NotNull Runnable runnable) {
    Runnable lastRunnable = runnable;

    for (int i = 0; i < myCount; i++) {
      final Editor editor = myEditors[i];
      final Runnable finalRunnable = lastRunnable;
      lastRunnable = new Runnable() {
        @Override
        public void run() {
          editor.getFoldingModel().runBatchFoldingOperation(new Runnable() {
            @Override
            public void run() {
              finalRunnable.run();
            }
          });
        }
      };
    }

    myDuringSynchronize = true;
    try {
      lastRunnable.run();
    }
    finally {
      myDuringSynchronize = false;
    }
  }

  public void destroy() {
    for (int i = 0; i < myCount; i++) {
      destroyFoldings(i);
    }

    for (FoldedBlock folding : myFoldings) {
      folding.destroyHighlighter();
    }
    myFoldings.clear();
  }

  private void destroyFoldings(final int index) {
    final FoldingModelEx model = myEditors[index].getFoldingModel();
    model.runBatchFoldingOperation(new Runnable() {
      @Override
      public void run() {
        for (FoldedBlock folding : myFoldings) {
          FoldRegion region = folding.getRegion(index);
          if (region != null) model.removeFoldRegion(region);
        }
      }
    });
  }

  //
  // Line numbers
  //

  public void onDocumentChanged(@NotNull DocumentEvent e) {
    if (StringUtil.indexOf(e.getOldFragment(), '\n') != -1 ||
        StringUtil.indexOf(e.getNewFragment(), '\n') != -1) {
      myShouldUpdateLineNumbers = true;
    }
  }

  @NotNull
  protected TIntFunction getLineConvertor(final int index) {
    return new TIntFunction() {
      @Override
      public int execute(int value) {
        updateLineNumbers(false);
        for (FoldedBlock folding : myFoldings) {
          int line = folding.getLine(index);
          if (line == -1) continue;
          if (line > value) break;
          FoldRegion region = folding.getRegion(index);
          if (line == value && region != null && !region.isExpanded()) return -1;
        }
        return value;
      }
    };
  }

  private void updateLineNumbers(boolean force) {
    if (!myShouldUpdateLineNumbers && !force) return;
    ApplicationManager.getApplication().assertReadAccessAllowed();
    for (FoldedBlock folding : myFoldings) {
      folding.updateLineNumbers();
    }
    myShouldUpdateLineNumbers = false;
  }

  //
  // Synchronized toggling of ranges
  //

  public void expandAll(final boolean expanded) {
    if (myDuringSynchronize) return;
    myDuringSynchronize = true;
    try {
      for (int i = 0; i < myCount; i++) {
        final int index = i;
        final FoldingModelEx model = myEditors[index].getFoldingModel();
        model.runBatchFoldingOperation(new Runnable() {
          @Override
          public void run() {
            for (FoldedBlock folding : myFoldings) {
              FoldRegion region = folding.getRegion(index);
              if (region != null) region.setExpanded(expanded);
            }
          }
        });
      }
    }
    finally {
      myDuringSynchronize = false;
    }
  }

  private class MyFoldingListener implements FoldingListener {
    private final int myIndex;
    @NotNull Set<FoldRegion> myModifiedRegions = new HashSet<FoldRegion>();

    public MyFoldingListener(int index) {
      myIndex = index;
    }

    @Override
    public void onFoldRegionStateChange(@NotNull FoldRegion region) {
      if (myDuringSynchronize) return;
      myModifiedRegions.add(region);
    }

    @Override
    public void onFoldProcessingEnd() {
      if (myModifiedRegions.isEmpty()) return;
      myDuringSynchronize = true;
      try {
        for (int i = 0; i < myCount; i++) {
          if (i == myIndex) continue;
          final int pairedIndex = i;
          myEditors[pairedIndex].getFoldingModel().runBatchFoldingOperation(new Runnable() {
            @Override
            public void run() {
              for (FoldedBlock folding : myFoldings) {
                FoldRegion region = folding.getRegion(myIndex);
                if (region == null || !region.isValid()) continue;
                if (myModifiedRegions.contains(region)) {
                  FoldRegion pairedRegion = folding.getRegion(pairedIndex);
                  if (pairedRegion == null || !pairedRegion.isValid()) continue;
                  pairedRegion.setExpanded(region.isExpanded());
                }
              }
            }
          });
        }

        myModifiedRegions.clear();
      }
      finally {
        myDuringSynchronize = false;
      }
    }
  }

  //
  // Highlighting
  //

  protected class MyPaintable implements DiffDividerDrawUtil.DividerSeparatorPaintable {
    private final int myLeft;
    private final int myRight;

    public MyPaintable(int left, int right) {
      myLeft = left;
      myRight = right;
    }

    @Override
    public void process(@NotNull Handler handler) {
      for (FoldedBlock folding : myFoldings) {
        FoldRegion region1 = folding.getRegion(myLeft);
        FoldRegion region2 = folding.getRegion(myRight);
        if (region1 == null || !region1.isValid() || region1.isExpanded()) continue;
        if (region2 == null || !region2.isValid() || region2.isExpanded()) continue;
        int line1 = myEditors[myLeft].getDocument().getLineNumber(region1.getStartOffset());
        int line2 = myEditors[myRight].getDocument().getLineNumber(region2.getStartOffset());
        if (!handler.process(line1, line2)) return;
      }
    }

    public void paintOnDivider(@NotNull Graphics2D gg, @NotNull Component divider) {
      DiffDividerDrawUtil.paintSeparators(gg, divider.getWidth(), myEditors[myLeft], myEditors[myRight], this);
    }

    public void paintOnScrollbar(@NotNull Graphics2D gg, int width) {
      DiffDividerDrawUtil.paintSeparatorsOnScrollbar(gg, width, myEditors[myLeft], myEditors[myRight], this);
    }
  }

  //
  // Cache
  //

  private class ExpandSuggester {
    @Nullable private final FoldingCache myCache;
    private final int[] myIndex = new int[myCount];
    private final boolean myDefault;

    public ExpandSuggester(@Nullable FoldingCache cache, boolean defaultValue) {
      myCache = cache;
      myDefault = defaultValue;
    }

    public boolean isExpanded(int[] starts, int[] ends) {
      if (myCache == null || myCache.ranges.length != myCount) return myDefault;
      if (myDefault != myCache.expandByDefault) return myDefault;

      Boolean state = null;
      for (int i = 0; i < myCount; i++) {
        Boolean sideState = getCachedExpanded(starts[i], ends[i], i);
        if (sideState == null) continue;
        if (state == null) {
          state = sideState;
          continue;
        }
        if (state != sideState) return myDefault;
      }
      return state == null ? myDefault : state;
    }

    @Nullable
    private Boolean getCachedExpanded(int start, int end, int index) {
      if (start == end) return null;

      List<FoldedRangeState> ranges = myCache.ranges[index];
      for (; myIndex[index] < ranges.size(); myIndex[index]++) {
        FoldedRangeState range = ranges.get(myIndex[index]);
        if (range.endLine <= start) continue;
        if (range.startLine >= end) return null;
        if (range.startLine <= start && range.endLine >= end) {
          return range.expanded;
        }
      }
      return null;
    }
  }

  public void updateContext(@NotNull UserDataHolder context, boolean defaultState) {
    if (myFoldings.isEmpty()) return; // do not rewrite cache by initial state
    context.putUserData(CACHE_KEY, getFoldingCache(defaultState));
  }

  @NotNull
  private FoldingCache getFoldingCache(final boolean defaultState) {
    return ApplicationManager.getApplication().runReadAction(new Computable<FoldingCache>() {
      @Override
      public FoldingCache compute() {
        List<FoldedRangeState>[] result = new List[myCount];
        for (int i = 0; i < myCount; i++) {
          result[i] = getFoldedRanges(i);
        }
        return new FoldingCache(result, defaultState);
      }
    });
  }

  @NotNull
  private List<FoldedRangeState> getFoldedRanges(int index) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    List<FoldedRangeState> ranges = new ArrayList<FoldedRangeState>();
    for (FoldedBlock folding : myFoldings) {
      FoldRegion region = folding.getRegion(index);
      if (region == null || !region.isValid()) continue;
      DocumentEx document = myEditors[index].getDocument();
      int line1 = document.getLineNumber(region.getStartOffset());
      int line2 = document.getLineNumber(region.getEndOffset()) + 1;
      ranges.add(new FoldedRangeState(line1, line2, region.isExpanded()));
    }
    return ranges;
  }

  private static class FoldingCache {
    public final boolean expandByDefault;
    @NotNull public final List<FoldedRangeState>[] ranges;

    public FoldingCache(@NotNull List<FoldedRangeState>[] ranges, boolean expandByDefault) {
      this.ranges = ranges;
      this.expandByDefault = expandByDefault;
    }
  }

  private static class FoldedRangeState {
    public final int startLine;
    public final int endLine;
    public final boolean expanded;

    public FoldedRangeState(int startLine, int endLine, boolean expanded) {
      this.startLine = startLine;
      this.endLine = endLine;
      this.expanded = expanded;
    }
  }

  //
  // Impl
  //

  protected class FoldedBlock {
    @NotNull private final FoldRegion[] myRegions;
    @NotNull private final int[] myLines;
    @NotNull private final List<RangeHighlighter> myHighlighters = new ArrayList<RangeHighlighter>(myCount);

    public FoldedBlock(@NotNull FoldRegion[] regions) {
      assert regions.length == myCount;
      myRegions = regions;
      myLines = new int[myCount];

      installHighlighter();
    }

    public void installHighlighter() {
      assert myHighlighters.isEmpty();

      for (int i = 0; i < myCount; i++) {
        FoldRegion region = myRegions[i];
        if (region == null || !region.isValid()) continue;
        myHighlighters.add(createFoldingHighlighter(myEditors[i], region));
      }
    }

    public void destroyHighlighter() {
      for (RangeHighlighter highlighter : myHighlighters) {
        highlighter.dispose();
      }
      myHighlighters.clear();
    }

    public void updateLineNumbers() {
      for (int i = 0; i < myCount; i++) {
        FoldRegion region = myRegions[i];
        if (region == null || !region.isValid()) {
          myLines[i] = -1;
        }
        else {
          myLines[i] = myEditors[i].getDocument().getLineNumber(region.getStartOffset());
        }
      }
    }

    @Nullable
    public FoldRegion getRegion(int index) {
      return myRegions[index];
    }

    public int getLine(int index) {
      return myLines[index];
    }
  }

  //
  // Helpers
  //

  private static int bound(int value, int min, int max) {
    return Math.min(Math.max(value, min), max);
  }

  @Nullable
  @Contract("null, _ -> null; !null, _ -> !null")
  protected static <T, V> Iterator<V> map(@Nullable final List<T> list, @NotNull final Function<T, V> mapping) {
    if (list == null) return null;
    final Iterator<T> it = list.iterator();
    return new Iterator<V>() {
      @Override
      public boolean hasNext() {
        return it.hasNext();
      }

      @Override
      public V next() {
        return mapping.fun(it.next());
      }

      @Override
      public void remove() {
      }
    };
  }

  @NotNull
  private static RangeHighlighter createFoldingHighlighter(@NotNull final Editor editor, @NotNull final FoldRegion region) {
    return DiffDrawUtil.createLineSeparatorHighlighter(editor, region.getStartOffset(), region.getEndOffset(), new BooleanGetter() {
      @Override
      public boolean get() {
        return region.isValid() && !region.isExpanded() && ((FoldingModelEx)editor.getFoldingModel()).isFoldingEnabled();
      }
    });
  }
}
