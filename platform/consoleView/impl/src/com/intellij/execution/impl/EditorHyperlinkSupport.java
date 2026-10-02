// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.ide.OccurenceNavigator;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorCoreUtil;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Expirable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.NavigatableAdapter;
import com.intellij.util.CommonProcessors;
import com.intellij.util.FilteringProcessor;
import com.intellij.util.Processor;
import com.intellij.util.SlowOperations;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import kotlin.Unit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;

import java.awt.Cursor;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.intellij.execution.filters.HyperlinkInfoBaseKt.navigate;

/**
 * Provides hyperlink support in editors.
 * <p> 
 * Invisible links ({@link Filter.ResultItem#isInvisibleLink()}) appear as normal text
 * until Ctrl/Cmd is pressed. They require Ctrl+Click to navigate and are excluded from
 * keyboard shortcuts and occurrence navigation.
 */
public final class EditorHyperlinkSupport {
  private static final Logger LOG = Logger.getInstance(EditorHyperlinkSupport.class);
  private static final Key<HyperlinkData> HYPERLINK = Key.create("EDITOR_HYPERLINK_SUPPORT_HYPERLINK");
  private static final Key<Unit> HIGHLIGHTING = Key.create("EDITOR_HYPERLINK_SUPPORT_HIGHLIGHTING");
  private static final Key<Unit> INLAY = Key.create("EDITOR_HYPERLINK_SUPPORT_INLAY");
  private static final Key<EditorHyperlinkSupport> EDITOR_HYPERLINK_SUPPORT_KEY = Key.create("EDITOR_HYPERLINK_SUPPORT_KEY");
  private static final Expirable ETERNAL_TOKEN = () -> false;

  private final EditorEx myEditor;
  private final @NotNull Project myProject;
  private final EditorHyperlinkInteraction myLinkInteraction;
  private final AsyncFilterRunner myFilterRunner;

  private final CopyOnWriteArrayList<EditorHyperlinkListener> myHyperlinkListeners = new CopyOnWriteArrayList<>();

  /**
   * If your editor has a project inside, better use {@link #get(Editor)}
   */
  public EditorHyperlinkSupport(@NotNull Editor editor, @NotNull Project project) {
    this(editor, project, false);
  }

  private EditorHyperlinkSupport(@NotNull Editor editor, @NotNull Project project, boolean trackChangesManually) {
    myEditor = (EditorEx)editor;
    myProject = project;
    Disposable editorDisposable = Disposer.newDisposable(project);
    EditorUtil.disposeWithEditor(editor, editorDisposable);
    myLinkInteraction = new EditorHyperlinkInteraction(myEditor, new MyEffectSupplier(), editorDisposable);
    myFilterRunner = new AsyncFilterRunner(this, myEditor, trackChangesManually);

    editor.addEditorMouseListener(new EditorMouseListener() {
      private MouseEvent myInitialMouseEvent;

      @Override
      public void mousePressed(@NotNull EditorMouseEvent e) {
        myInitialMouseEvent = e.getMouseEvent();
      }

      @Override
      public void mouseReleased(@NotNull EditorMouseEvent e) {
        MouseEvent initialMouseEvent = myInitialMouseEvent;
        myInitialMouseEvent = null;
        MouseEvent mouseEvent = e.getMouseEvent();
        if (mouseEvent.getButton() == MouseEvent.BUTTON1 &&
            !mouseEvent.isPopupTrigger() &&
            e.getCollapsedFoldRegion() == null) {

          if (initialMouseEvent != null && (mouseEvent.getComponent() != initialMouseEvent.getComponent() ||
                                       !mouseEvent.getPoint().equals(initialMouseEvent.getPoint()))) {
            return;
          }

          Runnable runnable = getLinkNavigationRunnable(e.getLogicalPosition(), e);
          if (runnable != null) {
            try (AccessToken ignore = SlowOperations.startSection(SlowOperations.ACTION_PERFORM)) {
              runnable.run();
            }
            e.consume();
          }
        }
      }

      @Override
      public void mouseExited(@NotNull EditorMouseEvent event) {
        myLinkInteraction.linkHovered(null, event);
      }
    });

    editor.addEditorMouseMotionListener(new EditorMouseMotionListener() {
      @Override
      public void mouseMoved(@NotNull EditorMouseEvent e) {
        RangeHighlighter range = findLinkAt(e);
        HyperlinkInfo info = range == null ? null : getHyperlinkInfo(range);
        myEditor.setCustomCursor(EditorHyperlinkSupport.class, info == null ? null : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        myLinkInteraction.linkHovered(range, e);
      }
    });
  }

  private @Nullable RangeHighlighter findLinkAt(@NotNull EditorMouseEvent e) {
    if (e.getArea() == EditorMouseEventArea.EDITING_AREA && e.isOverText()) {
      return findLinkRangeAt(e.getOffset());
    }
    return null;
  }

  @ApiStatus.Internal
  public void addEditorHyperlinkListener(@NotNull EditorHyperlinkListener listener) {
    myHyperlinkListeners.add(listener);
  }

  @ApiStatus.Internal
  public void removeEditorHyperlinkListener(@NotNull EditorHyperlinkListener listener) {
    myHyperlinkListeners.remove(listener);
  }

  public static @NotNull EditorHyperlinkSupport get(@NotNull Editor editor) {
    return get(editor, false);
  }

  @ApiStatus.Internal
  public static @NotNull EditorHyperlinkSupport get(@NotNull Editor editor, boolean trackDocumentChangesManually) {
    EditorHyperlinkSupport instance = editor.getUserData(EDITOR_HYPERLINK_SUPPORT_KEY);
    if (instance == null) {
      Project project = editor.getProject();
      assert project != null;
      instance = new EditorHyperlinkSupport(editor, project, trackDocumentChangesManually);
      editor.putUserData(EDITOR_HYPERLINK_SUPPORT_KEY, instance);
    }
    return instance;
  }

  public void clearHyperlinks() {
    // TODO replace with `clearHyperlinks(0, myEditor.getDocument().getTextLength())`
    for (RangeHighlighter highlighter : getHyperlinks(0, myEditor.getDocument().getTextLength(), myEditor)) {
      removeHyperlink(highlighter);
    }
  }

  /**
   * Clears hyperlinks, highlightings and inlays within the specified range in the editor.
   * TODO make the endOffset exclusive for hyperlinks and highlightings after banning empty hyperlinks and highlightings.
   * Note the endOffset will still be inclusive for inlays.
   *
   * @param startOffset The starting offset of the range, inclusive.
   * @param endOffset   The ending offset of the range, inclusive.
   */
  @ApiStatus.Internal
  public void clearHyperlinks(int startOffset, int endOffset) {
    for (RangeHighlighter highlighter : getRangeHighlighters(startOffset, endOffset, true, true, myEditor)) {
      myEditor.getMarkupModel().removeHighlighter(highlighter);
    }
    for (Inlay<?> inlay : getInlays(startOffset, endOffset)) {
      Disposer.dispose(inlay);
    }
  }

  /**
   * Retrieves the inlays within the specified range in the editor (both offsets are inclusive).
   */
  @ApiStatus.Internal
  @VisibleForTesting
  public List<Inlay<?>> getInlays(int startOffset, int endOffset) {
    return myEditor.getInlayModel().getInlineElementsInRange(startOffset, endOffset).stream().filter(INLAY::isIn).toList();
  }

  @ApiStatus.Internal
  @TestOnly
  public List<Inlay<?>> collectAllInlays() {
    return getInlays(0, myEditor.getDocument().getTextLength());
  }

  @TestOnly
  @ApiStatus.Internal
  public void waitForPendingFilters(long timeoutMs) {
    myFilterRunner.waitForPendingFilters(timeoutMs);
  }

  /**
   * @deprecated use {@link #findAllHyperlinksOnLine(int)} instead
   */
  @Deprecated(forRemoval = true)
  public @NotNull Map<RangeHighlighter, HyperlinkInfo> getHyperlinks() {
    Map<RangeHighlighter, HyperlinkInfo> result = new LinkedHashMap<>();
    for (RangeHighlighter highlighter : getHyperlinks(0, myEditor.getDocument().getTextLength(), myEditor)) {
      HyperlinkInfo info = getHyperlinkInfo(highlighter);
      if (info != null) {
        result.put(highlighter, info);
      }
    }
    return result;
  }

  /**
   * Returns a runnable to navigate the hyperlink at the given logical position, or {@code null} if no navigable link exists.
   * <p>
   * <b>Note:</b> This method does not support invisible links ({@link Filter.ResultItem#isInvisibleLink()}).
   * Invisible links require Ctrl+Click interaction and will return {@code null} from this method.
   */
  public @Nullable Runnable getLinkNavigationRunnable(@NotNull LogicalPosition logical) {
    return getLinkNavigationRunnable(logical, null);
  }

  private @Nullable Runnable getLinkNavigationRunnable(@NotNull LogicalPosition logical, @Nullable EditorMouseEvent e) {
    if (EditorCoreUtil.inVirtualSpace(myEditor, logical)) {
      return null;
    }

    int positionOffset = myEditor.logicalPositionToOffset(logical);
    RangeHighlighterEx range = findLinkRangeAt(positionOffset);
    HyperlinkData hyperlinkData = range != null ? getHyperlinkData(range) : null;
    if (hyperlinkData != null) {
      if (range.getEndOffset() == positionOffset) return null;
      HyperlinkInfo hyperlinkInfo = hyperlinkData.hyperlinkInfo;
      Runnable navigateAction = () -> {
        navigate(hyperlinkInfo, myProject, myEditor, logical);
        fireListeners(hyperlinkInfo);
      };
      if (e != null) {
        return () -> followLink(range, e, navigateAction);
      }
      else if (!hyperlinkData.isInvisibleLink) {
        return () -> {
          navigateAction.run();
          myLinkInteraction.onLinkFollowed(range);
        };
      }
    }
    return null;
  }

  private void fireListeners(@NotNull HyperlinkInfo info) {
    for (EditorHyperlinkListener listener : myHyperlinkListeners) {
      try {
        listener.hyperlinkActivated(info);
      }
      catch (CancellationException e) {
        throw e;
      }
      catch (Exception e) {
        LOG.error("The listener " + listener + " threw an exception", e);
      }
    }
  }

  public static @Nullable HyperlinkInfo getHyperlinkInfo(@NotNull RangeHighlighter range) {
    HyperlinkData attributes = getHyperlinkData(range);
    return attributes != null ? attributes.hyperlinkInfo() : null;
  }

  private static @Nullable HyperlinkData getHyperlinkData(@NotNull RangeHighlighter range) {
    return range.getUserData(HYPERLINK);
  }

  private @Nullable RangeHighlighterEx findLinkRangeAt(int offset) {
    // It should be synced with c.i.o.editor.impl.view.IterationState.LayerComparator.compare()
    Ref<RangeHighlighterEx> result = new Ref<>();
    processHyperlinksAndHighlightings(offset, offset, myEditor, true, false, highlighter -> {
      if (result.isNull()) {
        result.set(highlighter);
      }
      else {
        RangeHighlighterEx preferred = choosePreferredLink(result.get(), highlighter);
        result.set(preferred);
      }
      return true;
    });
    return result.get();
  }

  private static @NotNull RangeHighlighterEx choosePreferredLink(@NotNull RangeHighlighterEx link1, @NotNull RangeHighlighterEx link2) {
    HyperlinkData data1 = getHyperlinkData(link1);
    HyperlinkData data2 = getHyperlinkData(link2);
    boolean invisible1 = data1 != null && data1.isInvisibleLink;
    boolean invisible2 = data2 != null && data2.isInvisibleLink;
    if (invisible1 != invisible2) {
      return invisible1 ? link2 : link1; // prefer visible link
    }
    var range1 = link1.getTextRange();
    var range2 = link2.getTextRange();
    // Choose the smaller one. In case of equal sizes, prefer the left one.
    // In case the sizes and positions are identical, prefer the first one.
    // That's how IterationState works de facto.
    if (range1.getLength() < range2.getLength() ||
        range1.getLength() == range2.getLength() && range1.getStartOffset() <= range2.getStartOffset()) {
      return link1;
    }
    return link2;
  }

  public @Nullable HyperlinkInfo getHyperlinkAt(int offset) {
    RangeHighlighter range = findLinkRangeAt(offset);
    return range == null ? null : getHyperlinkInfo(range);
  }

  public @NotNull List<RangeHighlighter> findAllHyperlinksOnLine(int line) {
    int lineStart = myEditor.getDocument().getLineStartOffset(line);
    int lineEnd = myEditor.getDocument().getLineEndOffset(line);
    return getHyperlinks(lineStart, lineEnd, myEditor);
  }

  /**
   * Retrieves the hyperlinks within the specified range in the editor (both offsets are inclusive).
   */
  private static @NotNull List<RangeHighlighter> getHyperlinks(int startOffset, int endOffset, @NotNull Editor editor) {
    return getRangeHighlighters(startOffset, endOffset, true, false, editor);
  }

  @ApiStatus.Internal
  @TestOnly
  public @NotNull List<RangeHighlighter> getAllHyperlinks(int startOffset, int endOffset) {
    return getHyperlinks(startOffset, endOffset, myEditor);
  }

  /**
   * Retrieves hyperlinks / highlightings within the specified range in the editor (both offsets are inclusive).
   */
  @VisibleForTesting
  @ApiStatus.Internal
  public static @NotNull List<RangeHighlighter> getRangeHighlighters(int startOffset, int endOffset,
                                                              boolean hyperlinks,
                                                              boolean highlightings,
                                                              @NotNull Editor editor) {
    List<RangeHighlighter> result = new ArrayList<>();
    CommonProcessors.CollectProcessor<RangeHighlighter> processor = new CommonProcessors.CollectProcessor<>(result);
    processHyperlinksAndHighlightings(startOffset, endOffset, editor, hyperlinks, highlightings, processor);
    return result;
  }

  /**
   * Processes hyperlinks and highlightings within the specified range in the editor (both offsets are inclusive).
   */
  private static void processHyperlinksAndHighlightings(int startOffset,
                                                        int endOffset,
                                                        @NotNull Editor editor,
                                                        @SuppressWarnings("SameParameterValue")
                                                        boolean hyperlinks,
                                                        boolean highlightings,
                                                        @NotNull Processor<? super RangeHighlighterEx> processor) {
    MarkupModelEx markupModel = (MarkupModelEx)editor.getMarkupModel();
    markupModel.processRangeHighlightersOverlappingWith(startOffset, endOffset, new FilteringProcessor<>(
      highlighter -> highlighter.isValid() &&
                     ((hyperlinks && getHyperlinkInfo(highlighter) != null) ||
                      (highlightings && HIGHLIGHTING.isIn(highlighter))), processor));
  }

  public void removeHyperlink(@NotNull RangeHighlighter hyperlink) {
    myEditor.getMarkupModel().removeHighlighter(hyperlink);
  }

  public @Nullable HyperlinkInfo getHyperlinkInfoByLineAndCol(int line, int col) {
    return getHyperlinkAt(myEditor.logicalPositionToOffset(new LogicalPosition(line, col)));
  }

  public void createHyperlink(@NotNull RangeHighlighter highlighter, @NotNull HyperlinkInfo hyperlinkInfo) {
    associateHyperlink(highlighter, hyperlinkInfo, null, null, true, false);
  }

  public @NotNull RangeHighlighter createHyperlink(int highlightStartOffset,
                                                   int highlightEndOffset,
                                                   @Nullable TextAttributes highlightAttributes,
                                                   @NotNull HyperlinkInfo hyperlinkInfo) {
    return createHyperlink(highlightStartOffset, highlightEndOffset, highlightAttributes, hyperlinkInfo, null, null,
                           HighlighterLayer.HYPERLINK, false);
  }

  private @NotNull RangeHighlighter createHyperlink(int highlightStartOffset,
                                                    int highlightEndOffset,
                                                    @Nullable TextAttributes highlightAttributes,
                                                    @NotNull HyperlinkInfo hyperlinkInfo,
                                                    @Nullable TextAttributes followedHyperlinkAttributes,
                                                    @Nullable TextAttributes hoveredHyperlinkAttributes,
                                                    int layer,
                                                    boolean isInvisibleLink) {
    TextAttributesKey textAttributesKey = isInvisibleLink ? null : CodeInsightColors.HYPERLINK_ATTRIBUTES;
    return myEditor.getMarkupModel().addRangeHighlighterAndChangeAttributes(textAttributesKey,
                                                                            highlightStartOffset,
                                                                            highlightEndOffset,
                                                                            layer,
                                                                            HighlighterTargetArea.EXACT_RANGE,
                                                                            false, ex -> {
        if (highlightAttributes != null) {
          ex.setTextAttributes(highlightAttributes);
        }
        associateHyperlink(ex, hyperlinkInfo, followedHyperlinkAttributes, hoveredHyperlinkAttributes, false, isInvisibleLink);
      });
  }

  private static void associateHyperlink(@NotNull RangeHighlighter highlighter,
                                         @NotNull HyperlinkInfo hyperlinkInfo,
                                         @Nullable TextAttributes followedHyperlinkAttributes,
                                         @Nullable TextAttributes hoveredHyperlinkAttributes,
                                         boolean fireChanged,
                                         boolean isInvisibleLink) {
    HyperlinkData attributes = new HyperlinkData(
      highlighter, hyperlinkInfo, followedHyperlinkAttributes, hoveredHyperlinkAttributes, isInvisibleLink
    );
    highlighter.putUserData(HYPERLINK, attributes);
    if (fireChanged) {
      ((RangeHighlighterEx)highlighter).fireChanged(false, false, false);
    }
  }

  public @Nullable HyperlinkInfo getHyperlinkInfoByPoint(Point p) {
    LogicalPosition pos = myEditor.xyToLogicalPosition(new Point(p.x, p.y));
    if (EditorCoreUtil.inVirtualSpace(myEditor, pos)) {
      return null;
    }

    return getHyperlinkInfoByLineAndCol(pos.line, pos.column);
  }

  public @Nullable HyperlinkInfo getHyperlinkInfoByEvent(@NotNull EditorMouseEvent event) {
    return event.isOverText() ? getHyperlinkAt(event.getOffset()) : null;
  }

  /**
   * Starts jobs for highlighting hyperlinks using a custom filter.
   *
   * @param customFilter   the custom filter to apply for highlighting hyperlinks
   * @param startLine      the starting line index for highlighting hyperlinks, inclusive
   * @param endLine        the ending line index for highlighting hyperlinks, inclusive
   * @param expirableToken the token to expire the highlighting job. Expires when new re-highlighting started
   *                       (see {@link  com.intellij.execution.impl.ConsoleViewImpl#rehighlightHyperlinksAndFoldings()})
   */
  public void highlightHyperlinksLater(@NotNull Filter customFilter, int startLine, int endLine, @NotNull Expirable expirableToken) {
    myFilterRunner.highlightHyperlinks(myProject, customFilter, Math.max(0, startLine), endLine, expirableToken);
  }

  /**
   * @deprecated use {@link #highlightHyperlinksLater} instead
   */
  @Deprecated
  public void highlightHyperlinks(@NotNull Filter customFilter, int startLine, int endLine) {
    myFilterRunner.highlightHyperlinks(myProject, customFilter, Math.max(0, startLine), endLine, ETERNAL_TOKEN);
  }

  @ApiStatus.Internal
  public void highlightHyperlinks(@NotNull Filter.Result result) {
    highlightHyperlinks(result, item -> TextRange.create(item.getHighlightStartOffset(), item.getHighlightEndOffset()));
  }

  @ApiStatus.Internal
  public void highlightHyperlinks(@NotNull Filter.Result result,
                                  @NotNull Function<Filter.ResultItem, @Nullable TextRange> highlightingRangeAccessor) {
    int length = myEditor.getDocument().getTextLength();
    List<Pair<InlayProvider, Integer>> inlayProviderPairs = new SmartList<>();
    for (Filter.ResultItem resultItem : result.getResultItems()) {
      TextRange textRange = highlightingRangeAccessor.apply(resultItem);
      if (textRange == null) {
        continue;
      }
      int start = textRange.getStartOffset();
      int end = textRange.getEndOffset();
      if (start < 0 || end < start || end > length) {
        continue;
      }

      TextAttributes attributes = resultItem.getHighlightAttributes();
      if (resultItem instanceof InlayProvider inlayProvider) {
        inlayProviderPairs.add(Pair.create(inlayProvider, end));
      }
      else if (resultItem.getHyperlinkInfo() != null) {
        createHyperlink(start, end, attributes, resultItem.getHyperlinkInfo(), resultItem.getFollowedHyperlinkAttributes(),
                        resultItem.getHoveredHyperlinkAttributes(),
                        resultItem.getHighlighterLayer(),
                        resultItem.isInvisibleLink());
      }
      else if (attributes != null) {
        addHighlighter(start, end, attributes, resultItem.getHighlighterLayer());
      }
    }
    // add inlays in a batch if needed
    if (!inlayProviderPairs.isEmpty()) {
      myEditor.getInlayModel().execute(inlayProviderPairs.size() > 100, () -> {
        for (Pair<InlayProvider, Integer> inlayProviderPair : inlayProviderPairs) {
          addInlay(inlayProviderPair.second, inlayProviderPair.first);
        }
      });
    }
  }

  public void addHighlighter(int highlightStartOffset, int highlightEndOffset, TextAttributes highlightAttributes) {
    addHighlighter(highlightStartOffset, highlightEndOffset, highlightAttributes, HighlighterLayer.CONSOLE_FILTER);
  }

  public void addHighlighter(int highlightStartOffset, int highlightEndOffset, TextAttributes highlightAttributes, int highlighterLayer) {
    RangeHighlighter h = myEditor.getMarkupModel().addRangeHighlighter(highlightStartOffset, highlightEndOffset,
                                                                       highlighterLayer, highlightAttributes,
                                                                       HighlighterTargetArea.EXACT_RANGE);
    HIGHLIGHTING.set(h, Unit.INSTANCE);
  }

  private void addInlay(int offset, @NotNull InlayProvider inlayProvider) {
    Inlay<?> inlay = inlayProvider.createInlay(myEditor, offset);
    if (inlay != null) {
      INLAY.set(inlay, Unit.INSTANCE);
    }
  }

  private static class MyEffectSupplier implements EditorHyperlinkEffectSupplier {
    @Override
    public @Nullable TextAttributes getFollowedHyperlinkAttributes(@NotNull RangeHighlighterEx highlighter) {
      HyperlinkData data = getHyperlinkData(highlighter);
      return data == null ? null : data.followedHyperlinkAttributes();
    }

    @Override
    public @Nullable TextAttributes getHoveredHyperlinkAttributes(@NotNull RangeHighlighterEx highlighter) {
      HyperlinkData data = getHyperlinkData(highlighter);
      return data == null ? null : data.hoveredHyperlinkAttributes();
    }

    @Override
    public boolean isInvisibleLink(@NotNull RangeHighlighterEx highlighter) {
      HyperlinkData data = getHyperlinkData(highlighter);
      return data != null && data.isInvisibleLink;
    }
  }

  @ApiStatus.Internal
  public @Nullable OccurenceNavigator.OccurenceInfo getNextOccurrence(
    int delta,
    @NotNull Consumer<? super RangeHighlighter> action
  ) {
    List<HyperlinkData> links = ContainerUtil.mapNotNull(
      getHyperlinks(0, myEditor.getDocument().getTextLength(), myEditor),
      range -> {
        HyperlinkData data = getHyperlinkData(range);
        return data != null && !data.isInvisibleLink && data.hyperlinkInfo.includeInOccurenceNavigation() ? data : null;
      }
    );
    int nextInd = findNextOccurrenceIndex(links, delta > 0);
    if (nextInd >= 0) {
      HyperlinkData nextLink = links.get(nextInd);
      return new OccurenceNavigator.OccurenceInfo(new NavigatableAdapter() {
        @Override
        public void navigate(boolean requestFocus) {
          if (nextLink.rangeHighlighter.isValid()) {
            action.accept(nextLink.rangeHighlighter);
            if (nextLink.rangeHighlighter instanceof RangeHighlighterEx rangeHighlighterEx) {
              myLinkInteraction.onLinkFollowed(rangeHighlighterEx);
            }
          }
        }
      }, nextInd + 1, links.size());
    }
    return null;
  }

  /**
   * Finds the index of the next navigable occurrence, wrapping around the list.
   * Skips the currently followed link and any links in collapsed regions.
   */
  private int findNextOccurrenceIndex(@NotNull List<HyperlinkData> links, boolean forward) {
    if (links.isEmpty()) {
      return -1;
    }
    RangeHighlighter lastFollowedLink = myLinkInteraction.getLastFollowedLink();
    int startInd = Math.max(ContainerUtil.indexOf(links, link -> link.rangeHighlighter == lastFollowedLink), 0);
    int sign = forward ? 1 : -1;
    for (int i = 1; i <= links.size(); i++) {
      int ind = Math.floorMod(startInd + sign * i, links.size());
      HyperlinkData link = links.get(ind);
      RangeHighlighter range = link.rangeHighlighter;
      boolean inCollapsedRegion = myEditor.getFoldingModel().getCollapsedRegionAtOffset(range.getStartOffset()) != null;
      if (!inCollapsedRegion) {
        return ind;
      }
    }
    return -1;
  }

  private void followLink(@NotNull RangeHighlighterEx link, @NotNull EditorMouseEvent e, @NotNull Runnable action) {
    if (link.isValid()) {
      myLinkInteraction.followLink(link, e, () -> {
        action.run();
        return Unit.INSTANCE;
      });
    }
  }

  public static @NotNull String getLineText(@NotNull Document document, int lineNumber, boolean includeEol) {
    return getLineSequence(document, lineNumber, includeEol).toString();
  }

  private static @NotNull CharSequence getLineSequence(@NotNull Document document, int lineNumber, boolean includeEol) {
    int endOffset = document.getLineEndOffset(lineNumber);
    if (includeEol && endOffset < document.getTextLength()) {
      endOffset++;
    }
    return document.getImmutableCharSequence().subSequence(document.getLineStartOffset(lineNumber), endOffset);
  }

  private record HyperlinkData(
    @NotNull RangeHighlighter rangeHighlighter,
    @NotNull HyperlinkInfo hyperlinkInfo,
    @Nullable TextAttributes followedHyperlinkAttributes,
    @Nullable TextAttributes hoveredHyperlinkAttributes,
    boolean isInvisibleLink
  ) {
  }
}
