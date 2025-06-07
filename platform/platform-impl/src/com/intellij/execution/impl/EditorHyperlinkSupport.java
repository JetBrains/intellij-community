// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.HyperlinkInfoBase;
import com.intellij.ide.OccurenceNavigator;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.pom.NavigatableAdapter;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import kotlin.Unit;
import org.jetbrains.annotations.*;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

public final class EditorHyperlinkSupport {
  private static final Logger LOG = Logger.getInstance(EditorHyperlinkSupport.class);
  private static final Key<TextAttributes> OLD_HYPERLINK_TEXT_ATTRIBUTES = Key.create("OLD_HYPERLINK_TEXT_ATTRIBUTES");
  private static final Key<HyperlinkInfoTextAttributes> HYPERLINK = Key.create("EDITOR_HYPERLINK_SUPPORT_HYPERLINK");
  private static final Key<Unit> HIGHLIGHTING = Key.create("EDITOR_HYPERLINK_SUPPORT_HIGHLIGHTING");
  private static final Key<Unit> INLAY = Key.create("EDITOR_HYPERLINK_SUPPORT_INLAY");
  private static final Key<EditorHyperlinkSupport> EDITOR_HYPERLINK_SUPPORT_KEY = Key.create("EDITOR_HYPERLINK_SUPPORT_KEY");
  private static final Expirable ETERNAL_TOKEN = () -> false;

  private final EditorEx myEditor;
  private final @NotNull Project myProject;
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
        if (mouseEvent.getButton() == MouseEvent.BUTTON1 && !mouseEvent.isPopupTrigger()) {
          if (initialMouseEvent != null && (mouseEvent.getComponent() != initialMouseEvent.getComponent() ||
                                       !mouseEvent.getPoint().equals(initialMouseEvent.getPoint()))) {
            return;
          }

          Runnable runnable = getLinkNavigationRunnable(e.getLogicalPosition());
          if (runnable != null) {
            try (AccessToken ignore = SlowOperations.startSection(SlowOperations.ACTION_PERFORM)) {
              runnable.run();
            }
            e.consume();
          }
        }
      }
    });

    editor.addEditorMouseMotionListener(new EditorMouseMotionListener() {
      @Override
      public void mouseMoved(@NotNull EditorMouseEvent e) {
        if (e.getArea() != EditorMouseEventArea.EDITING_AREA) return;
        HyperlinkInfo info = getHyperlinkInfoByEvent(e);
        myEditor.setCustomCursor(EditorHyperlinkSupport.class, info == null ? null : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      }
    }
    );
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

  public @Nullable Runnable getLinkNavigationRunnable(@NotNull LogicalPosition logical) {
    if (EditorCoreUtil.inVirtualSpace(myEditor, logical)) {
      return null;
    }

    int positionOffset = myEditor.logicalPositionToOffset(logical);
    RangeHighlighter range = findLinkRangeAt(positionOffset);
    if (range != null) {
      if (range.getEndOffset() == positionOffset) return null;
      HyperlinkInfo hyperlinkInfo = getHyperlinkInfo(range);
      if (hyperlinkInfo != null) {
        return () -> {
          if (hyperlinkInfo instanceof HyperlinkInfoBase) {
            Point point = myEditor.logicalPositionToXY(logical);
            MouseEvent event = new MouseEvent(myEditor.getContentComponent(), 0, 0, 0, point.x, point.y, 1, false);
            ((HyperlinkInfoBase)hyperlinkInfo).navigate(myProject, new RelativePoint(event));
          }
          else {
            hyperlinkInfo.navigate(myProject);
          }
          linkFollowed(myEditor, getHyperlinks(0, myEditor.getDocument().getTextLength(),myEditor), range);
          fireListeners(hyperlinkInfo);
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
    HyperlinkInfoTextAttributes attributes = range.getUserData(HYPERLINK);
    return attributes != null ? attributes.hyperlinkInfo() : null;
  }

  private @Nullable RangeHighlighter findLinkRangeAt(int offset) {
    Ref<RangeHighlighter> ref = Ref.create();
    processHyperlinksAndHighlightings(offset, offset, myEditor, true, false, range -> {
      ref.set(range);
      return false;
    });
    return ref.get();
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
                                                        @NotNull Processor<? super RangeHighlighter> processor) {
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
    associateHyperlink(highlighter, hyperlinkInfo, null, true);
  }

  public @NotNull RangeHighlighter createHyperlink(int highlightStartOffset,
                                                   int highlightEndOffset,
                                                   @Nullable TextAttributes highlightAttributes,
                                                   @NotNull HyperlinkInfo hyperlinkInfo) {
    return createHyperlink(highlightStartOffset, highlightEndOffset, highlightAttributes, hyperlinkInfo, null,
                           HighlighterLayer.HYPERLINK);
  }

  private @NotNull RangeHighlighter createHyperlink(int highlightStartOffset,
                                                    int highlightEndOffset,
                                                    @Nullable TextAttributes highlightAttributes,
                                                    @NotNull HyperlinkInfo hyperlinkInfo,
                                                    @Nullable TextAttributes followedHyperlinkAttributes,
                                                    int layer) {
    return myEditor.getMarkupModel().addRangeHighlighterAndChangeAttributes(CodeInsightColors.HYPERLINK_ATTRIBUTES,
                                                                            highlightStartOffset,
                                                                            highlightEndOffset,
                                                                            layer,
                                                                            HighlighterTargetArea.EXACT_RANGE,
                                                                            false, ex -> {
        if (highlightAttributes != null) {
          ex.setTextAttributes(highlightAttributes);
        }
        associateHyperlink(ex, hyperlinkInfo, followedHyperlinkAttributes, false);
      });
  }

  private static void associateHyperlink(@NotNull RangeHighlighter highlighter,
                                         @NotNull HyperlinkInfo hyperlinkInfo,
                                         @Nullable TextAttributes followedHyperlinkAttributes,
                                         boolean fireChanged) {
    HyperlinkInfoTextAttributes attributes = new HyperlinkInfoTextAttributes(hyperlinkInfo, followedHyperlinkAttributes);
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
                        resultItem.getHighlighterLayer());
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

  private static @NotNull TextAttributes getFollowedHyperlinkAttributes(@NotNull RangeHighlighter range) {
    HyperlinkInfoTextAttributes attrs = range.getUserData(HYPERLINK);
    TextAttributes result = attrs == null ? null : attrs.followedHyperlinkAttributes();
    if (result == null) {
      result = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.FOLLOWED_HYPERLINK_ATTRIBUTES);
    }
    return result;
  }

  public static @Nullable OccurenceNavigator.OccurenceInfo getNextOccurrence(@NotNull Editor editor,
                                                                             int delta,
                                                                             @NotNull Consumer<? super RangeHighlighter> action) {
    List<RangeHighlighter> ranges = getHyperlinks(0, editor.getDocument().getTextLength(),editor);
    if (ranges.isEmpty()) {
      return null;
    }
    int i = ContainerUtil.indexOf(ranges, range -> range.getUserData(OLD_HYPERLINK_TEXT_ATTRIBUTES) != null);
    if (i == -1) {
      i = 0;
    }
    int newIndex = i;
    while (newIndex < ranges.size()) {
      newIndex = (newIndex + delta + ranges.size()) % ranges.size();
      RangeHighlighter next = ranges.get(newIndex);
      HyperlinkInfo info = getHyperlinkInfo(next);
      assert info != null;
      if (info.includeInOccurenceNavigation()) {
        boolean inCollapsedRegion = editor.getFoldingModel().getCollapsedRegionAtOffset(next.getStartOffset()) != null;
        if (!inCollapsedRegion) {
          return new OccurenceNavigator.OccurenceInfo(new NavigatableAdapter() {
            @Override
            public void navigate(boolean requestFocus) {
              action.consume(next);
              linkFollowed(editor, ranges, next);
            }
          }, newIndex + 1, ranges.size());
        }
      }
      if (newIndex == i) {
        break; // cycled through everything, found no next/prev hyperlink
      }
    }
    return null;
  }

  private static void linkFollowed(@NotNull Editor editor, @NotNull Collection<? extends RangeHighlighter> ranges, @NotNull RangeHighlighter link) {
    MarkupModelEx markupModel = (MarkupModelEx)editor.getMarkupModel();
    for (RangeHighlighter range : ranges) {
      TextAttributes oldAttr = range.getUserData(OLD_HYPERLINK_TEXT_ATTRIBUTES);
      if (oldAttr != null) {
        markupModel.setRangeHighlighterAttributes(range, oldAttr);
        range.putUserData(OLD_HYPERLINK_TEXT_ATTRIBUTES, null);
      }
      if (range == link) {
        range.putUserData(OLD_HYPERLINK_TEXT_ATTRIBUTES, range.getTextAttributes(editor.getColorsScheme()));
        markupModel.setRangeHighlighterAttributes(range, getFollowedHyperlinkAttributes(range));
      }
    }
    //refresh highlighter text attributes
    markupModel.addRangeHighlighter(CodeInsightColors.HYPERLINK_ATTRIBUTES, 0, 0, link.getLayer(), HighlighterTargetArea.EXACT_RANGE).dispose();
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

  private record HyperlinkInfoTextAttributes(@NotNull HyperlinkInfo hyperlinkInfo, @Nullable TextAttributes followedHyperlinkAttributes) {
  }
}
