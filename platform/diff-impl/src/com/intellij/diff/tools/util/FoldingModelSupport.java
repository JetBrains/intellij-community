// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.util;

import com.intellij.codeInsight.breadcrumbs.FileBreadcrumbsCollector;
import com.intellij.diff.tools.util.base.TextDiffViewerUtil;
import com.intellij.diff.util.DiffDividerDrawUtil;
import com.intellij.diff.util.DiffDrawUtil;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.LineRange;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FoldingListener;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.components.breadcrumbs.Crumb;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.concurrency.NonUrgentExecutor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.breadcrumbs.NavigatableCrumb;
import gnu.trove.TIntFunction;
import org.jetbrains.annotations.*;

import java.awt.*;
import java.util.List;
import java.util.*;

import static com.intellij.diff.util.DiffUtil.getLineCount;
import static com.intellij.openapi.diagnostic.Logger.getInstance;

/**
 * This class allows to add custom foldings to hide unchanged regions in diff.
 * EditorSettings#isAutoCodeFoldingEnabled() should be true, to avoid collisions with language-specific foldings
 *    (as it's impossible to create partially overlapped folding regions)
 *
 * @see DiffUtil#setFoldingModelSupport(EditorEx)
 */
public class FoldingModelSupport {
  private static final Logger LOG = getInstance(FoldingModelSupport.class);

  private static final String PLACEHOLDER = "     ";

  private static final Key<FoldingCache> CACHE_KEY = Key.create("Diff.FoldingUtil.Cache");

  protected final int myCount;
  @Nullable protected final Project myProject;
  protected final EditorEx @NotNull [] myEditors;

  @NotNull protected final List<FoldedGroup> myFoldings = new ArrayList<>();

  private boolean myDuringSynchronize;
  private final boolean[] myShouldUpdateLineNumbers;

  public FoldingModelSupport(@Nullable Project project, EditorEx @NotNull [] editors, @NotNull Disposable disposable) {
    myProject = project;
    myEditors = editors;
    myCount = myEditors.length;
    myShouldUpdateLineNumbers = new boolean[myCount];

    MyDocumentListener documentListener = new MyDocumentListener();
    List<Document> documents = ContainerUtil.map(myEditors, EditorEx::getDocument);
    TextDiffViewerUtil.installDocumentListeners(documentListener, documents, disposable);

    for (int i = 0; i < myCount; i++) {
      if (myCount > 1) {
        myEditors[i].getFoldingModel().addListener(new MyFoldingListener(i), disposable);
      }
    }
  }

  public int getCount() {
    return myCount;
  }

  //
  // Init
  //

  /*
   * Iterator returns ranges of changed lines: start1, end1, start2, end2, ...
   */
  @Nullable
  protected Data computeFoldedRanges(@Nullable final Iterator<int[]> changedLines,
                                     @NotNull final Settings settings) {
    if (changedLines == null || settings.range == -1) return null;

    FoldingBuilder builder = new FoldingBuilder(myEditors, settings);
    return builder.build(changedLines);
  }

  /*
   * Iterator returns ranges of changed lines: start1, end1, start2, end2, ...
   */
  protected void install(@Nullable final Iterator<int[]> changedLines,
                         @Nullable final UserDataHolder context,
                         @NotNull final Settings settings) {
    Data data = computeFoldedRanges(changedLines, settings);
    install(data, context, settings);
  }

  public void install(@Nullable final Data data,
                      @Nullable final UserDataHolder context,
                      @NotNull final Settings settings) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    for (FoldedBlock folding : getFoldedBlocks()) {
      folding.destroyHighlighter();
    }

    runBatchOperation(() -> {
      for (FoldedBlock folding : getFoldedBlocks()) {
        folding.destroyFolding();
      }
      myFoldings.clear();


      if (data != null) {
        FoldingInstaller installer = new FoldingInstaller(context, settings);
        installer.install(data);
      }
    });

    updateLineNumbers(true);
  }

  protected static int[] countLines(EditorEx @NotNull [] editors) {
    return ReadAction.compute(() -> {
      int[] lineCount = new int[editors.length];
      for (int i = 0; i < editors.length; i++) {
        lineCount[i] = getLineCount(editors[i].getDocument());
      }
      return lineCount;
    });
  }

  private static final class FoldingBuilder extends FoldingBuilderBase {
    private final EditorEx @NotNull [] myEditors;

    private FoldingBuilder(EditorEx @NotNull [] editors, @NotNull Settings settings) {
      super(countLines(editors), settings);
      myEditors = editors;
    }

    @Nullable
    @Override
    protected FoldedRangeDescription getDescription(@NotNull Project project, int lineNumber, int index) {
      return getLineSeparatorDescription(project, myEditors[index].getDocument(), lineNumber);
    }
  }

  protected abstract static class FoldingBuilderBase {
    @NotNull private final Settings mySettings;
    private final int @NotNull [] myLineCount;
    private final int myCount;

    @NotNull private final List<Data.Group> myGroups = new ArrayList<>();

    public FoldingBuilderBase(int[] lineCount, @NotNull Settings settings) {
      mySettings = settings;
      myLineCount = lineCount;
      myCount = lineCount.length;
    }

    @NotNull
    public Data build(@NotNull final Iterator<int[]> changedLines) {
      int[] starts = new int[myCount];
      int[] ends = new int[myCount];

      int[] last = new int[myCount];
      for (int i = 0; i < myCount; i++) {
        last[i] = Integer.MIN_VALUE;
      }

      while (changedLines.hasNext()) {
        int[] offsets = changedLines.next();

        for (int i = 0; i < myCount; i++) {
          starts[i] = last[i];
          ends[i] = offsets[i * 2];
          last[i] = offsets[i * 2 + 1];
        }
        addGroup(starts, ends);
      }

      for (int i = 0; i < myCount; i++) {
        starts[i] = last[i];
        ends[i] = Integer.MAX_VALUE;
      }
      addGroup(starts, ends);

      return new Data(myGroups, (project, line, index) -> getDescription(project, line, index));
    }

    private void addGroup(int[] starts, int[] ends) {
      List<Data.Block> result = new ArrayList<>(3);
      int[] rangeStarts = new int[myCount];
      int[] rangeEnds = new int[myCount];

      for (int number = 0; ; number++) {
        int shift = getRangeShift(mySettings.range, number);
        if (shift == -1) break;

        for (int i = 0; i < myCount; i++) {
          rangeStarts[i] = DiffUtil.bound(starts[i] + shift, 0, myLineCount[i]);
          rangeEnds[i] = DiffUtil.bound(ends[i] - shift, 0, myLineCount[i]);
        }
        ContainerUtil.addIfNotNull(result, createBlock(rangeStarts, rangeEnds));
      }

      if (result.size() > 0) {
        myGroups.add(new Data.Group(result));
      }
    }

    @Nullable
    private Data.Block createBlock(int[] starts, int[] ends) {
      LineRange[] regions = new LineRange[myCount];

      for (int i = 0; i < myCount; i++) {
        if (ends[i] - starts[i] < 2) continue;
        regions[i] = new LineRange(starts[i], ends[i]);
      }
      boolean hasFolding = ContainerUtil.or(regions, Objects::nonNull);
      if (!hasFolding) return null;

      return new Data.Block(regions);
    }

    @Nullable
    protected abstract FoldedRangeDescription getDescription(@NotNull Project project, int lineNumber, int index);
  }

  @Nullable
  private static String getRangeDescription(@NotNull Project project,
                                            int startLine,
                                            int endLine,
                                            int index,
                                            @NotNull DescriptionComputer computer) {
    if (startLine == endLine) return null;

    FoldedRangeDescription endDescription = computer.computeDescription(project, endLine, index);
    if (endDescription == null) return null;

    FoldedRangeDescription startDescription = computer.computeDescription(project, startLine, index);
    if (Comparing.equal(startDescription, endDescription) &&
        !(endDescription.anchorLine != -1 && startLine <= endDescription.anchorLine)) {
      return null;
    }
    return endDescription.description;
  }

  @Nullable
  protected static FoldedRangeDescription getLineSeparatorDescription(@NotNull Project project,
                                                                      @NotNull Document document,
                                                                      int lineNumber) {
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (psiFile == null) return null;
    VirtualFile virtualFile = psiFile.getVirtualFile();

    if (document.getLineCount() <= lineNumber) return null;
    int offset = document.getLineStartOffset(lineNumber);

    FileBreadcrumbsCollector collector = FileBreadcrumbsCollector.findBreadcrumbsCollector(project, virtualFile);
    List<Crumb> crumbs = ContainerUtil.newArrayList(collector.computeCrumbs(virtualFile, document, offset, true));
    if (crumbs.isEmpty()) return null;

    String description = StringUtil.join(crumbs, it -> it.getText(), " > ");

    Crumb lastCrumb = crumbs.get(crumbs.size() - 1);
    int anchorOffset = lastCrumb instanceof NavigatableCrumb ? ((NavigatableCrumb)lastCrumb).getAnchorOffset() : -1;
    int anchorLine = anchorOffset != -1 ? document.getLineNumber(anchorOffset) : -1;

    return new FoldedRangeDescription(description, anchorLine);
  }

  protected static final class FoldedRangeDescription {
    @NotNull private final String description;
    private final int anchorLine;

    private FoldedRangeDescription(@NotNull String description, int anchorLine) {
      this.description = description;
      this.anchorLine = anchorLine;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      FoldedRangeDescription that = (FoldedRangeDescription)o;
      return Objects.equals(description, that.description) &&
             Objects.equals(anchorLine, that.anchorLine);
    }

    @Override
    public int hashCode() {
      return Objects.hash(description, anchorLine);
    }
  }

  private class FoldingInstaller {
    @NotNull private final ExpandSuggester myExpandSuggester;

    FoldingInstaller(@Nullable UserDataHolder context, @NotNull Settings settings) {
      FoldingCache cache = context != null ? context.getUserData(CACHE_KEY) : null;
      myExpandSuggester = new ExpandSuggester(cache, settings.defaultExpanded);
    }

    public void install(@NotNull Data data) {
      for (Data.Group group : data.groups) {
        List<FoldedBlock> blocks = new ArrayList<>(3);

        for (Data.Block block : group.blocks) {
          ContainerUtil.addIfNotNull(blocks, createBlock(data, block, myExpandSuggester.isExpanded(block)));
        }

        if (blocks.size() > 0) {
          FoldedGroup foldedGroup = new FoldedGroup(blocks);
          for (FoldedBlock folding : foldedGroup.blocks) {
            folding.installHighlighter(foldedGroup);
          }
          myFoldings.add(foldedGroup);
        }
      }
    }

    @Nullable
    private FoldedBlock createBlock(@NotNull Data data, @NotNull Data.Block block, boolean expanded) {
      FoldRegion[] regions = new FoldRegion[myCount];
      String[] cachedDescriptions = null;
      for (int i = 0; i < myCount; i++) {
        LineRange range = block.ranges[i];
        if (range != null) regions[i] = addFolding(myEditors[i], range.start, range.end, expanded);
      }

      boolean hasFolding = ContainerUtil.or(regions, Objects::nonNull);
      boolean hasExpanded = ContainerUtil.or(regions, region -> region != null && region.isExpanded());

      // do not desync regions on runBatchFoldingOperationDoNotCollapseCaret
      if (hasExpanded && !expanded) {
        for (FoldRegion region : regions) {
          if (region != null) region.setExpanded(true);
        }
      }

      if (!hasExpanded && !expanded) {
        cachedDescriptions = new String[myCount];
        for (int i = 0; i < myCount; i++) {
          LineRange range = block.ranges[i];
          if (range != null) {
            cachedDescriptions[i] = myExpandSuggester.getCachedDescription(range.start, range.end, i);
          }
        }
      }

      return hasFolding ? new FoldedBlock(regions, data.descriptionComputer, cachedDescriptions) : null;
    }
  }

  @Nullable
  public static FoldRegion addFolding(@NotNull EditorEx editor, int start, int end, boolean expanded) {
    DocumentEx document = editor.getDocument();
    final int startOffset = document.getLineStartOffset(start);
    final int endOffset = document.getLineEndOffset(end - 1);

    FoldRegion value = editor.getFoldingModel().addFoldRegion(startOffset, endOffset, PLACEHOLDER);
    if (value != null) {
      value.setExpanded(expanded);
      value.setInnerHighlightersMuted(true);
    }
    return value;
  }

  private void runBatchOperation(@NotNull Runnable runnable) {
    Runnable lastRunnable = runnable;

    for (EditorEx editor : myEditors) {
      final Runnable finalRunnable = lastRunnable;
      lastRunnable = () -> {
        if (DiffUtil.isFocusedComponent(editor.getComponent())) {
          editor.getFoldingModel().runBatchFoldingOperationDoNotCollapseCaret(finalRunnable);
        }
        else {
          editor.getFoldingModel().runBatchFoldingOperation(finalRunnable);
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
    for (FoldedBlock folding : getFoldedBlocks()) {
      folding.destroyHighlighter();
    }

    runBatchOperation(() -> {
      for (FoldedBlock folding : getFoldedBlocks()) {
        folding.destroyFolding();
      }
      myFoldings.clear();
    });
  }

  //
  // Line numbers
  //

  private class MyDocumentListener implements DocumentListener {
    @Override
    public void documentChanged(@NotNull DocumentEvent e) {
      if (StringUtil.indexOf(e.getOldFragment(), '\n') != -1 ||
          StringUtil.indexOf(e.getNewFragment(), '\n') != -1) {
        for (int i = 0; i < myCount; i++) {
          if (myEditors[i].getDocument() == e.getDocument()) {
            myShouldUpdateLineNumbers[i] = true;
          }
        }
      }
    }
  }

  @NotNull
  public TIntFunction getLineConvertor(final int index) {
    return value -> {
      updateLineNumbers(false);
      for (FoldedBlock folding : getFoldedBlocks()) { // TODO: avoid full scan - it could slowdown painting
        int line = folding.getLine(index);
        if (line == -1) continue;
        if (line > value) break;
        FoldRegion region = folding.getRegion(index);
        if (line == value && region != null && !region.isExpanded()) return -1;
      }
      return value;
    };
  }

  private void updateLineNumbers(boolean force) {
    for (int i = 0; i < myCount; i++) {
      if (!myShouldUpdateLineNumbers[i] && !force) continue;
      myShouldUpdateLineNumbers[i] = false;

      ApplicationManager.getApplication().assertReadAccessAllowed();
      for (FoldedBlock folding : getFoldedBlocks()) {
        folding.updateLineNumber(i);
      }
    }
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
        model.runBatchFoldingOperation(() -> {
          for (FoldedBlock folding : getFoldedBlocks()) {
            FoldRegion region = folding.getRegion(index);
            if (region != null) region.setExpanded(expanded);
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
    @NotNull private final Set<FoldRegion> myModifiedRegions = new HashSet<>();

    MyFoldingListener(int index) {
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
          myEditors[pairedIndex].getFoldingModel().runBatchFoldingOperation(() -> {
            for (FoldedBlock folding : getFoldedBlocks()) {
              FoldRegion region = folding.getRegion(myIndex);
              if (region == null || !region.isValid()) continue;
              if (myModifiedRegions.contains(region)) {
                FoldRegion pairedRegion = folding.getRegion(pairedIndex);
                if (pairedRegion == null || !pairedRegion.isValid()) continue;
                pairedRegion.setExpanded(region.isExpanded());
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
      for (FoldedGroup group : myFoldings) {
        for (FoldedBlock folding : group.blocks) {
          FoldRegion region1 = folding.getRegion(myLeft);
          FoldRegion region2 = folding.getRegion(myRight);
          if (region1 == null || !region1.isValid() || region1.isExpanded()) continue;
          if (region2 == null || !region2.isValid() || region2.isExpanded()) continue;
          int line1 = myEditors[myLeft].getDocument().getLineNumber(region1.getStartOffset());
          int line2 = myEditors[myRight].getDocument().getLineNumber(region2.getStartOffset());
          if (!handler.process(line1, line2)) return;
          break;
        }
      }
    }

    public void paintOnDivider(@NotNull Graphics2D gg, @NotNull Component divider) {
      DiffDividerDrawUtil.paintSeparators(gg, divider.getWidth(), myEditors[myLeft], myEditors[myRight], this);
    }
  }

  //
  // Cache
  //

  /*
   * To Cache:
   * For each block of foldings (foldings for a single unchanged block in diff) we remember biggest expanded and biggest collapsed range.
   *
   * From Cache:
   * We look into cache while building ranges, trying to find corresponding range in cached state.
   * "Corresponding range" now is just smallest covering range.
   *
   * If document was modified since cache creation, this will lead to strange results. But this is a rare case, and we can't do anything with it.
   */

  private class ExpandSuggester {
    @Nullable private final FoldingCache myCache;
    private final int[] myIndex = new int[myCount];
    private final boolean myDefault;

    ExpandSuggester(@Nullable FoldingCache cache, boolean defaultValue) {
      myCache = cache;
      myDefault = defaultValue;
    }

    public boolean isExpanded(@NotNull Data.Block block) {
      if (myCache == null || myCache.ranges.length != myCount) return myDefault;
      if (myDefault != myCache.expandByDefault) return myDefault;

      Boolean state = null;
      for (int index = 0; index < myCount; index++) {
        LineRange range = block.ranges[index];
        if (range == null) continue;
        Boolean sideState = getCachedExpanded(range.start, range.end, index);
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

      FoldedGroupState range = getCachedState(start, end, index);
      if (range == null) return null;

      if (range.collapsed != null && range.collapsed.contains(start, end)) return false;
      if (range.expanded != null && range.expanded.contains(start, end)) return true;

      assert false : "Invalid LineRange" + range.expanded + ", " + range.collapsed + ", " + new LineRange(start, end);
      return null;
    }

    @Nullable
    public String getCachedDescription(int start, int end, int index) {
      if (myCache == null || myCache.ranges.length != myCount) return null;

      FoldedGroupState range = getCachedState(start, end, index);
      if (range == null) return null;

      if (range.collapsed != null && range.collapsed.contains(start, end)) {
        return range.collapsedDescription != null ? range.collapsedDescription[index] : null;
      }

      return null;
    }

    @Nullable
    private FoldedGroupState getCachedState(int start, int end, int index) {
      if (start == end) return null;

      //noinspection ConstantConditions
      List<FoldedGroupState> ranges = myCache.ranges[index];
      for (; myIndex[index] < ranges.size(); myIndex[index]++) {
        FoldedGroupState range = ranges.get(myIndex[index]);
        LineRange lineRange = range.getLineRange();

        if (lineRange.end <= start) continue;
        if (lineRange.contains(start, end)) return range;
        if (lineRange.start >= start) return null; // we could need current range for enclosing next-level foldings
      }
      return null;
    }
  }

  @CalledInAwt
  public void updateContext(@NotNull UserDataHolder context, @NotNull final Settings settings) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myFoldings.isEmpty()) return; // do not rewrite cache by initial state
    context.putUserData(CACHE_KEY, getFoldingCache(settings));
  }

  @NotNull
  @CalledInAwt
  private FoldingCache getFoldingCache(@NotNull Settings settings) {
    //noinspection unchecked
    List<FoldedGroupState>[] result = new List[myCount];
    for (int i = 0; i < myCount; i++) {
      result[i] = collectFoldedGroupsStates(i);
    }
    return new FoldingCache(result, settings.defaultExpanded);
  }

  @NotNull
  private List<FoldedGroupState> collectFoldedGroupsStates(int index) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    List<FoldedGroupState> ranges = new ArrayList<>();
    DocumentEx document = myEditors[index].getDocument();

    for (FoldedGroup group : myFoldings) {
      LineRange expanded = null;
      LineRange collapsed = null;
      String[] collapsedDescription = null;

      for (FoldedBlock folding : group.blocks) {
        FoldRegion region = folding.getRegion(index);
        if (region == null || !region.isValid()) continue;
        if (region.isExpanded()) {
          if (expanded == null) {
            int line1 = document.getLineNumber(region.getStartOffset());
            int line2 = document.getLineNumber(region.getEndOffset()) + 1;

            expanded = new LineRange(line1, line2);
          }
        }
        else {
          int line1 = document.getLineNumber(region.getStartOffset());
          int line2 = document.getLineNumber(region.getEndOffset()) + 1;
          collapsed = new LineRange(line1, line2);
          collapsedDescription = ContainerUtil.map(folding.myDescriptions,
                                                   it -> it != null ? it.get() : null,
                                                   ArrayUtil.EMPTY_STRING_ARRAY);
          break;
        }
      }

      if (expanded != null || collapsed != null) {
        ranges.add(new FoldedGroupState(expanded, collapsed, collapsedDescription));
      }
    }
    return ranges;
  }

  private static class FoldingCache {
    public final boolean expandByDefault;
    public final List<FoldedGroupState> @NotNull [] ranges;

    FoldingCache(List<FoldedGroupState> @NotNull [] ranges, boolean expandByDefault) {
      this.ranges = ranges;
      this.expandByDefault = expandByDefault;
    }
  }

  public static final class Data {
    @NotNull private final List<Group> groups;
    @NotNull private final DescriptionComputer descriptionComputer;

    private Data(@NotNull List<Group> groups, @NotNull DescriptionComputer descriptionComputer) {
      this.groups = groups;
      this.descriptionComputer = descriptionComputer;
    }

    private static final class Group {
      @NotNull public final List<Block> blocks;

      private Group(@NotNull List<Block> blocks) {
        this.blocks = blocks;
      }
    }

    private static final class Block {
      public final LineRange @NotNull [] ranges;

      /**
       * WARN: arrays can have nullable values (ex: when unchanged fragments in editors have different length due to ignore policy)
       */
      private Block(LineRange @NotNull [] ranges) {
        this.ranges = ranges;
      }
    }
  }

  private interface DescriptionComputer {
    @Nullable
    FoldedRangeDescription computeDescription(@NotNull Project project, int lineNumber, int index);
  }

  /**
   * Stores topmost expanded and topmost collapsed ranges for a folded group, if any.
   */
  private static class FoldedGroupState {
    @Nullable public final LineRange expanded;
    @Nullable public final LineRange collapsed;
    public final String @Nullable [] collapsedDescription;

    FoldedGroupState(@Nullable LineRange expanded, @Nullable LineRange collapsed, String @Nullable [] collapsedDescription) {
      assert expanded != null || collapsed != null;

      this.expanded = expanded;
      this.collapsed = collapsed;
      this.collapsedDescription = collapsedDescription;
    }

    @NotNull
    public LineRange getLineRange() {
      //noinspection ConstantConditions
      return expanded != null ? expanded : collapsed;
    }
  }

  //
  // Impl
  //

  @NotNull
  private Iterable<FoldedBlock> getFoldedBlocks() {
    return () -> new Iterator<FoldedBlock>() {
      private int myGroupIndex = 0;
      private int myBlockIndex = 0;

      @Override
      public boolean hasNext() {
        return myGroupIndex < myFoldings.size();
      }

      @Override
      public FoldedBlock next() {
        FoldedGroup group = myFoldings.get(myGroupIndex);
        FoldedBlock folding = group.blocks.get(myBlockIndex);

        if (group.blocks.size() > myBlockIndex + 1) {
          myBlockIndex++;
        }
        else {
          myGroupIndex++;
          myBlockIndex = 0;
        }

        return folding;
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }

  /**
   * Stores folded blocks for a single unchanged region in text.
   * These blocks are enclosed one in another and are sorted from outer to inner.
   *
   * @see #getRangeShift that is used to calculate enclosed blocks ranges.
   */
  private static class FoldedGroup {
    @NotNull public final List<FoldedBlock> blocks;

    FoldedGroup(@NotNull List<FoldedBlock> blocks) {
      this.blocks = blocks;
    }
  }

  /**
   * Stores 'matching' fold regions in different Editors (array can contain `null` if Editor has no matching region).
   * These regions will be collapsed/expanded synchronously, see {@link MyFoldingListener}.
   */
  protected class FoldedBlock {
    private final FoldRegion @NotNull [] myRegions;
    private final int @NotNull [] myLines;

    @NotNull private final List<RangeHighlighter> myHighlighters = new ArrayList<>(myCount);

    private final LazyDescription @NotNull [] myDescriptions;
    private final ProgressIndicator myDescriptionsIndicator = new EmptyProgressIndicator();

    public FoldedBlock(FoldRegion @NotNull [] regions,
                       @NotNull DescriptionComputer descriptionComputer,
                       String @Nullable [] cachedDescriptions) {
      assert regions.length == myCount;
      assert cachedDescriptions == null || cachedDescriptions.length == myCount;
      myRegions = regions;
      myLines = new int[myCount];

      myDescriptions = new LazyDescription[myCount];
      if (myProject != null) {
        for (int i = 0; i < myCount; i++) {
          String cachedDescription = cachedDescriptions != null ? cachedDescriptions[i] : null;
          myDescriptions[i] = new LazyDescription(myProject, i, descriptionComputer, cachedDescription);
        }
      }
    }

    public void installHighlighter(@NotNull FoldedGroup group) {
      assert myHighlighters.isEmpty();

      for (int i = 0; i < myCount; i++) {
        FoldRegion region = myRegions[i];
        if (region == null || !region.isValid()) continue;
        myHighlighters.addAll(DiffDrawUtil.createLineSeparatorHighlighter(myEditors[i],
                                                                          region.getStartOffset(), region.getEndOffset(),
                                                                          getHighlighterCondition(group, i),
                                                                          myDescriptions[i]));
      }
    }

    public void destroyFolding() {
      for (int i = 0; i < myCount; i++) {
        FoldRegion region = myRegions[i];
        if (region != null) myEditors[i].getFoldingModel().removeFoldRegion(region);
      }
      myDescriptionsIndicator.cancel();
    }

    public void destroyHighlighter() {
      for (RangeHighlighter highlighter : myHighlighters) {
        highlighter.dispose();
      }
      myHighlighters.clear();
    }

    public void updateLineNumber(int index) {
      FoldRegion region = myRegions[index];
      if (region == null || !region.isValid()) {
        myLines[index] = -1;
      }
      else {
        myLines[index] = myEditors[index].getDocument().getLineNumber(region.getStartOffset());
      }
    }

    @Nullable
    public FoldRegion getRegion(int index) {
      return myRegions[index];
    }

    public int getLine(int index) {
      return myLines[index];
    }

    @NotNull
    private BooleanGetter getHighlighterCondition(@NotNull FoldedGroup group, final int index) {
      return () -> {
        if (!myEditors[index].getFoldingModel().isFoldingEnabled()) return false;

        for (FoldedBlock folding : group.blocks) {
          FoldRegion region = folding.getRegion(index);
          boolean visible = region != null && region.isValid() && !region.isExpanded();
          if (folding == this) return visible;
          if (visible) return false; // do not paint separator, if 'parent' folding is collapsed
        }
        return false;
      };
    }

    private class LazyDescription implements Computable<String> {
      @NotNull private final Project myProject;
      private final int myIndex;
      @NotNull private final DescriptionComputer myDescriptionComputer;

      @NotNull private RangeDescription myDescription;
      private boolean myLoadingStarted = false;

      LazyDescription(@NotNull Project project, int index, @NotNull DescriptionComputer descriptionComputer, @Nullable String cachedValue) {
        myProject = project;
        myIndex = index;
        myDescriptionComputer = descriptionComputer;
        myDescription = new RangeDescription(cachedValue);
      }

      @Override
      @CalledInAwt
      public String compute() {
        if (!myLoadingStarted) {
          myLoadingStarted = true;
          ReadAction
            .nonBlocking(() -> new RangeDescription(computeDescription()))
            .finishOnUiThread(ModalityState.any(), result -> {
              myDescription = result;
              if (result.description != null) repaintEditor();
            })
            .withDocumentsCommitted(myProject)
            .wrapProgress(myDescriptionsIndicator)
            .submit(NonUrgentExecutor.getInstance());
        }
        return myDescription.description;
      }

      private void repaintEditor() {
        FoldRegion region = myRegions[myIndex];
        if (region == null || !region.isValid()) return;
        if (myEditors[myIndex].isDisposed()) return;
        myEditors[myIndex].repaint(region.getStartOffset(), region.getEndOffset());
      }

      @Nullable
      @CalledInBackground
      private String computeDescription() {
        try {
          ProgressManager.checkCanceled();

          FoldRegion region = myRegions[myIndex];
          if (region == null) return null;

          // Regions can be disposed without taking WriteLock
          int startOffset = region.getStartOffset();
          int endOffset = region.getEndOffset();
          if (startOffset == -1 || endOffset == -1) return null;
          if (!region.isValid()) return null;

          int startLine = myEditors[myIndex].getDocument().getLineNumber(startOffset);
          int endLine = myEditors[myIndex].getDocument().getLineNumber(endOffset);
          return getRangeDescription(myProject, startLine, endLine, myIndex, myDescriptionComputer);
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Throwable e) {
          LOG.error(e);
          return null;
        }
      }

      @Nullable
      @CalledInAwt
      public String get() {
        return myDescription.description;
      }
    }
  }

  private static final class RangeDescription {
    @Nullable public final String description;

    private RangeDescription(@Nullable String description) {
      this.description = description;
    }
  }

  //
  // Helpers
  //

  /*
   * number - depth of folding insertion (from zero)
   * return: number of context lines. ('-1' - end)
   */
  private static int getRangeShift(int range, int number) {
    switch (number) {
      case 0:
        return range;
      case 1:
        return range * 2;
      case 2:
        return range * 4;
      default:
        return -1;
    }
  }

  @Nullable
  @Contract("null, _ -> null; !null, _ -> !null")
  protected static <T, V> Iterator<V> map(@Nullable final List<T> list, @NotNull final Function<? super T, ? extends V> mapping) {
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

  public static class Settings {
    public final int range;
    public final boolean defaultExpanded;

    public Settings(int range, boolean defaultExpanded) {
      this.range = range;
      this.defaultExpanded = defaultExpanded;
    }
  }
}
