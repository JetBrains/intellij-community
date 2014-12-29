package com.intellij.openapi.util.diff.tools.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FoldingListener;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.diff.comparison.iterables.DiffIterableUtil.IntPair;
import com.intellij.openapi.util.diff.fragments.LineFragment;
import com.intellij.openapi.util.diff.fragments.LineFragments;
import com.intellij.openapi.util.diff.fragments.MergeLineFragment;
import com.intellij.openapi.util.diff.util.BooleanGetter;
import com.intellij.openapi.util.diff.util.DiffDrawUtil;
import com.intellij.openapi.util.diff.util.Side;
import com.intellij.openapi.util.diff.util.ThreeSide;
import com.intellij.openapi.util.text.StringUtil;
import gnu.trove.TIntFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class FoldingModelSupport {
  public static class OnesideFoldingModel extends FoldingModelBase {
    public OnesideFoldingModel(@NotNull EditorEx editor, @NotNull Disposable disposable) {
      super(new EditorEx[]{editor}, disposable);
    }

    public void install(@Nullable List<IntPair> equalLines, @NotNull UserDataHolder context, boolean defaultExpanded, int range) {
      if (equalLines == null) return;
      if (range == -1) return;
      MyExpandSuggester suggester = new MyExpandSuggester(context.getUserData(CACHE_KEY), defaultExpanded);

      for (IntPair line : equalLines) {
        addRange(line.val1, line.val2, range, suggester);
      }

      updateLineNumbers();
    }

    private void addRange(int start1, int end1, int range, @NotNull MyExpandSuggester suggester) {
      int count1 = myEditors[0].getDocument().getLineCount();

      start1 = bound(start1 + range, 0, count1);
      end1 = bound(end1 - range, 0, count1);

      boolean expanded = suggester.getExpanded(start1, end1);

      FoldRegion region1 = addFolding(myEditors[0], start1, end1, expanded);
      if (region1 == null) return;
      myFoldings.add(new FoldedBlock(new FoldRegion[]{region1}));
    }

    private class MyExpandSuggester extends ExpandSuggesterBase {
      public MyExpandSuggester(@Nullable FoldingCache cache, boolean defaultValue) {
        super(cache, defaultValue);
      }

      public boolean getExpanded(int start1, int end1) {
        Boolean expanded1 = getCachedExpanded(start1, end1, 0);

        if (start1 == end1) return myDefault;
        if (expanded1 == null) return myDefault;
        return expanded1;
      }
    }

    @NotNull
    public TIntFunction getLineNumberConvertor() {
      return getLineConvertor(0);
    }
  }

  public static class SimpleFoldingModel extends FoldingModelBase {
    private final MyPaintable myPaintable = new MyPaintable(0, 1);

    public SimpleFoldingModel(@NotNull EditorEx editor1, @NotNull EditorEx editor2, @NotNull Disposable disposable) {
      super(new EditorEx[]{editor1, editor2}, disposable);

      for (int i = 0; i < myCount; i++) {
        myEditors[i].getFoldingModel().addListener(new MyListener(i), disposable);
        myEditors[i].getGutterComponentEx().setLineNumberConvertor(getLineConvertor(i));
      }
    }

    public void install(@Nullable LineFragments lineFragments, @NotNull UserDataHolder context, boolean defaultExpanded, int range) {
      if (lineFragments == null) return;
      if (range == -1) return;
      List<? extends LineFragment> fragments = lineFragments.getFragments();
      MyExpandSuggester suggester = new MyExpandSuggester(context.getUserData(CACHE_KEY), defaultExpanded);

      int last1 = Integer.MIN_VALUE;
      int last2 = Integer.MIN_VALUE;
      for (LineFragment fragment : fragments) {
        addRange(last1, fragment.getStartLine1(), last2, fragment.getStartLine2(), range, suggester);

        last1 = fragment.getEndLine1();
        last2 = fragment.getEndLine2();
      }
      addRange(last1, Integer.MAX_VALUE, last2, Integer.MAX_VALUE, range, suggester);

      updateLineNumbers();
    }

    private void addRange(int start1, int end1, int start2, int end2, int range, @NotNull MyExpandSuggester suggester) {
      int count1 = myEditors[0].getDocument().getLineCount();
      int count2 = myEditors[1].getDocument().getLineCount();

      start1 = bound(start1 + range, 0, count1);
      start2 = bound(start2 + range, 0, count2);
      end1 = bound(end1 - range, 0, count1);
      end2 = bound(end2 - range, 0, count2);

      boolean expanded = suggester.getExpanded(start1, end1, start2, end2);

      FoldRegion region1 = addFolding(myEditors[0], start1, end1, expanded);
      FoldRegion region2 = addFolding(myEditors[1], start2, end2, expanded);
      if (region1 == null && region2 == null) return;
      myFoldings.add(new FoldedBlock(new FoldRegion[]{region1, region2}));
    }

    private class MyExpandSuggester extends ExpandSuggesterBase {
      public MyExpandSuggester(@Nullable FoldingCache cache, boolean defaultValue) {
        super(cache, defaultValue);
      }

      public boolean getExpanded(int start1, int end1, int start2, int end2) {
        Boolean expanded1 = getCachedExpanded(start1, end1, 0);
        Boolean expanded2 = getCachedExpanded(start2, end2, 1);

        if (start1 == end1 && start2 == end2) return myDefault;
        if (start1 == end1) return expanded2 != null ? expanded2 : myDefault;
        if (start2 == end2) return expanded1 != null ? expanded1 : myDefault;

        if (expanded1 == null || expanded2 == null) return myDefault;
        return expanded1;
      }
    }

    public void paintOnDivider(@NotNull Graphics2D gg, @NotNull Component divider) {
      myPaintable.paintOnDivider(gg, divider);
    }
  }

  public static class SimpleThreesideFoldingModel extends FoldingModelBase {
    private final MyPaintable myPaintable1 = new MyPaintable(0, 1);
    private final MyPaintable myPaintable2 = new MyPaintable(1, 2);

    public SimpleThreesideFoldingModel(@NotNull EditorEx[] editors, @NotNull Disposable disposable) {
      super(editors, disposable);
      assert editors.length == 3;

      for (int i = 0; i < myCount; i++) {
        myEditors[i].getFoldingModel().addListener(new MyListener(i), disposable);
        myEditors[i].getGutterComponentEx().setLineNumberConvertor(getLineConvertor(i));
      }
    }

    public void install(@Nullable List<MergeLineFragment> fragments, @NotNull UserDataHolder context,
                        boolean defaultExpanded, int range) {
      if (fragments == null) return;
      if (range == -1) return;
      MyExpandSuggester suggester = new MyExpandSuggester(context.getUserData(CACHE_KEY), defaultExpanded);

      int last1 = Integer.MIN_VALUE;
      int last2 = Integer.MIN_VALUE;
      int last3 = Integer.MIN_VALUE;
      for (MergeLineFragment fragment : fragments) {
        addRange(last1, fragment.getStartLine(ThreeSide.LEFT),
                 last2, fragment.getStartLine(ThreeSide.BASE),
                 last3, fragment.getStartLine(ThreeSide.RIGHT),
                 range, suggester);

        last1 = fragment.getEndLine(ThreeSide.LEFT);
        last2 = fragment.getEndLine(ThreeSide.BASE);
        last3 = fragment.getEndLine(ThreeSide.RIGHT);
      }
      addRange(last1, Integer.MAX_VALUE,
               last2, Integer.MAX_VALUE,
               last3, Integer.MAX_VALUE,
               range, suggester);

      updateLineNumbers();
    }

    private void addRange(int start1, int end1, int start2, int end2, int start3, int end3,
                          int range, @NotNull MyExpandSuggester suggester) {
      int count1 = myEditors[0].getDocument().getLineCount();
      int count2 = myEditors[1].getDocument().getLineCount();
      int count3 = myEditors[2].getDocument().getLineCount();

      start1 = bound(start1 + range, 0, count1);
      start2 = bound(start2 + range, 0, count2);
      start3 = bound(start3 + range, 0, count3);
      end1 = bound(end1 - range, 0, count1);
      end2 = bound(end2 - range, 0, count2);
      end3 = bound(end3 - range, 0, count3);

      boolean expanded = suggester.getExpanded(start1, end1, start2, end2, start3, end3);

      FoldRegion region1 = addFolding(myEditors[0], start1, end1, expanded);
      FoldRegion region2 = addFolding(myEditors[1], start2, end2, expanded);
      FoldRegion region3 = addFolding(myEditors[2], start3, end3, expanded);
      if (region1 == null && region2 == null && region3 == null) return;
      myFoldings.add(new FoldedBlock(new FoldRegion[]{region1, region2, region3}));
    }

    private class MyExpandSuggester extends ExpandSuggesterBase {
      public MyExpandSuggester(@Nullable FoldingCache cache, boolean defaultValue) {
        super(cache, defaultValue);
      }

      public boolean getExpanded(int start1, int end1, int start2, int end2, int start3, int end3) {
        Boolean expanded1 = getCachedExpanded(start1, end1, 0);
        Boolean expanded2 = getCachedExpanded(start2, end2, 1);

        if (start1 == end1 && start2 == end2) return myDefault;
        if (start1 == end1) return expanded2 != null ? expanded2 : myDefault;
        if (start2 == end2) return expanded1 != null ? expanded1 : myDefault;

        if (expanded1 == null || expanded2 == null) return myDefault;
        return expanded1;
      }
    }

    public void paintOnDivider(@NotNull Graphics2D gg, @NotNull Component divider, @NotNull Side side) {
      MyPaintable paintable = side.selectN(myPaintable1, myPaintable2);
      paintable.paintOnDivider(gg, divider);
    }

    public void paintOnScrollbar(@NotNull Graphics2D gg, int width) {
      myPaintable2.paintOnScrollbar(gg, width);
    }
  }

  private static abstract class FoldingModelBase {
    protected static final Key<FoldingCache> CACHE_KEY = Key.create("Diff.FoldingUtil.SimpleCache");

    protected final int myCount;
    @NotNull protected final EditorEx[] myEditors;

    @NotNull protected final List<FoldedBlock> myFoldings = new ArrayList<FoldedBlock>();

    private boolean myDuringSynchronize;
    private boolean myShouldUpdateLineNumbers;

    public FoldingModelBase(@NotNull EditorEx[] editors, @NotNull Disposable disposable) {
      myEditors = editors;
      myCount = myEditors.length;
    }

    //
    // Init
    //

    @Nullable
    protected static FoldRegion addFolding(@NotNull final EditorEx editor, int start, int end, final boolean expanded) {
      if (end - start < 2) return null;

      DocumentEx document = editor.getDocument();
      final int startOffset = document.getLineStartOffset(start);
      final int endOffset = document.getLineEndOffset(end - 1);

      final Ref<FoldRegion> ref = new Ref<FoldRegion>();
      editor.getFoldingModel().runBatchFoldingOperation(new Runnable() {
        @Override
        public void run() {
          FoldRegion value = editor.getFoldingModel().addFoldRegion(startOffset, endOffset, "");
          if (value != null) value.setExpanded(expanded);
          ref.set(value);
        }
      });
      return ref.get();
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
          if (myShouldUpdateLineNumbers) updateLineNumbers();
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

    protected void updateLineNumbers() {
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

    private void updatePairedRegion(@NotNull FoldRegion region, int index, int pairedIndex) {
      FoldRegion pairedRegion = null;
      for (FoldedBlock folding : myFoldings) {
        if (folding.getRegion(index) == region) {
          pairedRegion = folding.getRegion(pairedIndex);
          break;
        }
      }
      if (pairedRegion == null) return;
      if (!region.isValid() || !pairedRegion.isValid()) return;
      pairedRegion.setExpanded(region.isExpanded());
    }

    protected class MyListener implements FoldingListener {
      private final int myIndex;
      @NotNull List<FoldRegion> myModifiedRegions = new ArrayList<FoldRegion>();

      public MyListener(int index) {
        myIndex = index;
      }

      @Override
      public void onFoldRegionStateChange(@NotNull FoldRegion region) {
        if (myDuringSynchronize) return;
        myModifiedRegions.add(region);
      }

      @Override
      public void onFoldProcessingEnd() {
        myDuringSynchronize = true;
        try {
          for (int i = 0; i < myCount; i++) {
            if (i == myIndex) continue;
            final int pairedIndex = i;
            myEditors[pairedIndex].getFoldingModel().runBatchFoldingOperation(new Runnable() {
              @Override
              public void run() {
                for (final FoldRegion region : myModifiedRegions) {
                  updatePairedRegion(region, myIndex, pairedIndex);
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

    protected class MyPaintable implements DividerPolygonUtil.DividerSeparatorPaintable {
      private final int myLeft;
      private final int myRight;

      public MyPaintable(int left, int right) {
        myLeft = left;
        myRight = right;
      }

      @Override
      public void process(@NotNull Handler handler) {
        for (FoldedBlock folding : myFoldings) {
          if (folding.myRegions[myLeft] == null || folding.myRegions[myRight] == null) continue;
          if (folding.myRegions[myLeft].isExpanded() || folding.myRegions[myRight].isExpanded()) continue;
          int line1 = myEditors[myLeft].getDocument().getLineNumber(folding.myRegions[myLeft].getStartOffset());
          int line2 = myEditors[myRight].getDocument().getLineNumber(folding.myRegions[myRight].getStartOffset());
          handler.process(line1, line2);
        }
      }

      public void paintOnDivider(@NotNull Graphics2D gg, @NotNull Component divider) {
        DividerPolygonUtil.paintSeparators(gg, divider.getWidth(), myEditors[myLeft], myEditors[myRight], this);
      }

      public void paintOnScrollbar(@NotNull Graphics2D gg, int width) {
        DividerPolygonUtil.paintSeparatorsOnScrollbar(gg, width, myEditors[myLeft], myEditors[myRight], this);
      }
    }

    //
    // Cache
    //

    protected abstract class ExpandSuggesterBase {
      @Nullable private final FoldingCache myCache;
      private int[] myIndex = new int[myCount];
      protected final boolean myDefault;

      public ExpandSuggesterBase(@Nullable FoldingCache cache, boolean defaultValue) {
        myCache = cache;
        myDefault = defaultValue;
      }

      @Nullable
      protected Boolean getCachedExpanded(int start, int end, int index) {
        if (myCache == null || myCache.myRanges.length != myCount) return null;
        if (start == end) return null;

        List<FoldedRange> ranges = myCache.myRanges[index];
        for (; myIndex[index] < ranges.size(); myIndex[index]++) {
          FoldedRange range = ranges.get(myIndex[index]);
          if (range.myEndLine <= start) continue;
          if (range.myStartLine >= end) return null;
          if (range.myStartLine < end && range.myEndLine > start) {
            return range.myExpanded;
          }
        }
        return null;
      }
    }

    public void updateContext(@NotNull UserDataHolder context) {
      if (myFoldings.isEmpty()) return; // do not rewrite cache by initial state
      context.putUserData(CACHE_KEY, getFoldingCache());
    }

    @NotNull
    private FoldingCache getFoldingCache() {
      List<FoldedRange>[] result = new List[myCount];
      for (int i = 0; i < myCount; i++) {
        result[i] = getFoldedRanges(i);
      }
      return new FoldingCache(result);
    }

    @NotNull
    private List<FoldedRange> getFoldedRanges(int index) {
      List<FoldedRange> ranges = new ArrayList<FoldedRange>(myFoldings.size());
      for (FoldedBlock folding : myFoldings) {
        FoldRegion region = folding.getRegion(index);
        if (region == null) continue;
        DocumentEx document = myEditors[index].getDocument();
        int line1 = document.getLineNumber(region.getStartOffset());
        int line2 = document.getLineNumber(region.getEndOffset()) + 1;
        ranges.add(new FoldedRange(line1, line2, region.isExpanded()));
      }
      return ranges;
    }

    protected static class FoldingCache {
      @NotNull public final List<FoldedRange>[] myRanges;

      public FoldingCache(@NotNull List<FoldedRange>[] ranges) {
        myRanges = ranges;
      }
    }

    //
    // Impl
    //

    protected class FoldedBlock {
      @NotNull protected final FoldRegion[] myRegions;
      @NotNull private final List<RangeHighlighter> myHighlighters = new ArrayList<RangeHighlighter>();

      private final int[] myLines = new int[myCount];

      public FoldedBlock(@NotNull FoldRegion[] regions) {
        assert regions.length == myCount;
        myRegions = regions;

        installHighlighter();
      }

      public void installHighlighter() {
        assert myHighlighters.isEmpty();

        for (int i = 0; i < myCount; i++) {
          FoldRegion region = myRegions[i];
          if (region == null) continue;
          myHighlighters.add(DiffDrawUtil.createLineSeparatorHighlighter(myEditors[i], region.getEndOffset(), new RangeCondition(region)));
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
          if (region == null) {
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
  }

  //
  // Helpers
  //

  private static int bound(int value, int min, int max) {
    return Math.min(Math.max(value, min), max);
  }

  private static class RangeCondition implements BooleanGetter {
    @NotNull private final FoldRegion myRegion;

    public RangeCondition(@NotNull FoldRegion region) {
      myRegion = region;
    }

    @Override
    public boolean get() {
      return !myRegion.isExpanded();
    }
  }

  private static class FoldedRange {
    public final int myStartLine;
    public final int myEndLine;
    public final boolean myExpanded;

    public FoldedRange(int startLine, int endLine, boolean expanded) {
      myStartLine = startLine;
      myEndLine = endLine;
      myExpanded = expanded;
    }
  }
}
