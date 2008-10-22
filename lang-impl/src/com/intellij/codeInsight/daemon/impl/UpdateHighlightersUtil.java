package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.intention.impl.FileLevelIntentionComponent;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import gnu.trove.THashMap;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class UpdateHighlightersUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil");

  private static final Key<List<HighlightInfo>> FILE_LEVEL_HIGHLIGHTS = Key.create("FILE_LEVEL_HIGHLIGHTS");
  private static final Comparator<TextRange> BY_START_OFFSET = new Comparator<TextRange>() {
    public int compare(final TextRange o1, final TextRange o2) {
      return o1.getStartOffset() - o2.getStartOffset();
    }
  };
  private static final Comparator<TextRange> BY_START_OFFSET_OR_CONTAINS = new Comparator<TextRange>() {
    public int compare(final TextRange o1, final TextRange o2) {
      if (o1.contains(o2) || o2.contains(o1)) return 0;
      return o1.getStartOffset() - o2.getStartOffset();
    }
  };
  private static final TObjectHashingStrategy<HighlightInfo> BY_RANGE_MARKER_OFFSET_FIRST = new TObjectHashingStrategy<HighlightInfo>() {
    public int computeHashCode(HighlightInfo info) {
      return info.getActualStartOffset();
    }

    public boolean equals(HighlightInfo info1, HighlightInfo info2) {
      return info1.getSeverity() == info2.getSeverity() &&
             areRangesEqual(info1, info2) &&
             info1.type.equals(info2.type) &&
             Comparing.equal(info1.getGutterIconRenderer(), info2.getGutterIconRenderer()) &&
             Comparing.equal(info1.forcedTextAttributes, info2.forcedTextAttributes) &&
             Comparing.strEqual(info1.description, info2.description)
        ;
    }
  };

  private static void cleanFileLevelHighlights(@NotNull Project project, final int group, PsiFile psiFile) {
    if (psiFile == null || !psiFile.getViewProvider().isPhysical()) return;
    VirtualFile vFile = psiFile.getViewProvider().getVirtualFile();
    final FileEditorManager manager = FileEditorManager.getInstance(project);
    for (FileEditor fileEditor : manager.getEditors(vFile)) {
      final List<HighlightInfo> infos = fileEditor.getUserData(FILE_LEVEL_HIGHLIGHTS);
      if (infos == null) continue;
      List<HighlightInfo> infosToRemove = new ArrayList<HighlightInfo>();
      for (HighlightInfo info : infos) {
        if (info.group == group) {
          manager.removeEditorAnnotation(fileEditor, info.fileLevelComponent);
          infosToRemove.add(info);
        }
      }
      infos.removeAll(infosToRemove);
    }
  }

  public static void setHighlightersToEditor(@NotNull Project project,
                                             @NotNull Document document,
                                             int startOffset,
                                             int endOffset,
                                             @NotNull Collection<HighlightInfo> highlights,
                                             int group) {
    setHighlightersToEditor(project, document, Collections.singletonMap(new TextRange(startOffset, endOffset), highlights), group);
  }

  public static void setHighlightersToEditor(@NotNull Project project,
                                             @NotNull Document document,
                                             @NotNull Map<TextRange, Collection<HighlightInfo>> infos,
                                             int group) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    //serialized implicitly by the dispatch thread

    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
    cleanFileLevelHighlights(project, group, psiFile);

    List<TextRange> ranges = new ArrayList<TextRange>(infos.keySet());
    Collections.sort(ranges, BY_START_OFFSET);
    //merge intersecting
    for (int i = 1; i < ranges.size(); i++) {
      TextRange range = ranges.get(i);
      TextRange prev = ranges.get(i-1);
      if (prev.intersects(range)) {
        ranges.remove(i);
        TextRange union = prev.union(range);

        Collection<HighlightInfo> collection = infos.get(prev);
        collection.addAll(infos.get(range));
        infos.remove(prev);
        infos.remove(range);
        infos.put(union, collection);
        ranges.set(i - 1, union);
        i--;
      }
    }

    List<HighlightInfo> oldHighlights = DaemonCodeAnalyzerImpl.getHighlights(document, project);
    List<HighlightInfo> result = new ArrayList<HighlightInfo>();
    Map<HighlightInfo, HighlightInfo> infosToRemove = new THashMap<HighlightInfo, HighlightInfo>(BY_RANGE_MARKER_OFFSET_FIRST);

    boolean changed = false;
    if (oldHighlights != null) {
      for (HighlightInfo info : oldHighlights) {
        RangeHighlighter highlighter = info.highlighter;
        boolean toRemove = !highlighter.isValid() ||
                           info.group == group && Collections.binarySearch(ranges, new TextRange(highlighter.getStartOffset(), highlighter.getEndOffset()),
                                                                           BY_START_OFFSET_OR_CONTAINS) >= 0;
        if (toRemove) {
          infosToRemove.put(info,info);
          changed = true;
        }
        else {
          result.add(info);
        }
      }
      if (LOG.isDebugEnabled()) {
        LOG.debug("Removed segment highlighters:" + (oldHighlights.size() - result.size()));
      }
    }
    MarkupModel markup = document.getMarkupModel(project);

    for (HighlightInfo info : getHighlightsToRemove(markup)) {
      infosToRemove.put(info, info);
    }

    List<HighlightInfo> reallyRemoved = new ArrayList<HighlightInfo>();
    Map<TextRange, RangeMarker> ranges2markersCache = new THashMap<TextRange, RangeMarker>(oldHighlights == null ? 10 : oldHighlights.size());
    for (TextRange range : ranges) {
      int rangeStartOffset = range.getStartOffset();
      int rangeEndOffset = range.getEndOffset();

      List<HighlightInfo> highlights = new ArrayList<HighlightInfo>(infos.get(range));
      Collections.sort(highlights, new Comparator<HighlightInfo>() {
        public int compare(HighlightInfo o1, HighlightInfo o2) {
          return o1.startOffset - o2.startOffset;
        }
      });

      for (HighlightInfo info : highlights) {
        if (info.isFileLevelAnnotation && psiFile != null && psiFile.getViewProvider().isPhysical()) {
          addFileLevelHighlight(project, group, info, psiFile);
          continue;
        }

        int infoStartOffset = info.startOffset;
        int infoEndOffset = info.endOffset;
        if (infoStartOffset < rangeStartOffset || infoEndOffset > rangeEndOffset) continue;

        if (infoEndOffset == infoStartOffset) {
          infoEndOffset++; //show something in case of empty highlightinfo
        }
        final int docLength = document.getTextLength();
        if (infoEndOffset > docLength) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Invalid HighlightInfo created: (" + infoStartOffset + ":" + infoEndOffset + ")" + info.description);
          }
          infoEndOffset = docLength;
        }

        info.text = document.getCharsSequence().subSequence(infoStartOffset, infoEndOffset).toString();
        info.group = group;

        HighlightInfo toRemove = infosToRemove.remove(info);
        if (toRemove != null) {
          info.highlighter = toRemove.highlighter;
          info.fixMarker = toRemove.fixMarker;
          info.quickFixActionMarkers = toRemove.quickFixActionMarkers;
          reallyRemoved.add(toRemove);
        }
        else {
          createRangeHighlighter(project, document, ranges2markersCache, psiFile, info, infoStartOffset, infoEndOffset, markup);
          changed = true;
        }

        result.add(info);
      }

      for (HighlightInfo info : infosToRemove.values()) {
        int infoStartOffset = info.getActualStartOffset();
        int infoEndOffset = info.getActualEndOffset();
        if (info.highlighter.isValid()) {
          if (infoStartOffset < rangeStartOffset || infoEndOffset > rangeEndOffset) continue;
          if (info.group != group) continue;
        }
        markup.removeHighlighter(info.highlighter);
        changed = true;
        reallyRemoved.add(info);
      }
    }

    if (changed) {
      List<HighlightInfo> toRemove = getHighlightsToRemove(markup);
      toRemove.removeAll(reallyRemoved);

      DaemonCodeAnalyzerImpl.setHighlights(document, result, project);
      clearWhiteSpaceOptimizationFlag(document);
    }
  }

  private static boolean areRangesEqual(HighlightInfo info1, HighlightInfo info2) {
    int startOffset1 = info1.getActualStartOffset();
    int startOffset2 = info2.getActualStartOffset();
    int endOffset2 = info2.getActualEndOffset();
    int endOffset1 = info1.getActualEndOffset();
    return startOffset1 == startOffset2 && endOffset1 == endOffset2;
  }

  private static void createRangeHighlighter(Project project, Document document, Map<TextRange, RangeMarker> ranges2markersCache,
                                             PsiFile psiFile, HighlightInfo info, int infoStartOffset, int infoEndOffset, MarkupModel markup) {
    HighlightSeverity severity = info.getSeverity();
    int layer;
    if (severity == HighlightSeverity.WARNING) {
      layer = HighlighterLayer.WARNING;
    }
    else if (SeverityRegistrar.getInstance(project).compare(severity, HighlightSeverity.ERROR) >= 0) {
      layer = HighlighterLayer.ERROR;
    }
    else {
      layer = HighlighterLayer.ADDITIONAL_SYNTAX;
    }

    RangeHighlighterEx highlighter = (RangeHighlighterEx)markup.addRangeHighlighter(
      infoStartOffset,
      infoEndOffset,
      layer,
      info.getTextAttributes(psiFile),
      HighlighterTargetArea.EXACT_RANGE);

    info.highlighter = highlighter;
    highlighter.setAfterEndOfLine(info.isAfterEndOfLine);

    highlighter.setErrorStripeMarkColor(info.getErrorStripeMarkColor(psiFile));
    highlighter.setErrorStripeTooltip(info);
    highlighter.setGutterIconRenderer(info.getGutterIconRenderer());

    ranges2markersCache.put(new TextRange(infoStartOffset, infoEndOffset), info.highlighter);
    if (info.quickFixActionRanges != null) {
      info.quickFixActionMarkers = new ArrayList<Pair<HighlightInfo.IntentionActionDescriptor, RangeMarker>>(info.quickFixActionRanges.size());
      for (Pair<HighlightInfo.IntentionActionDescriptor, TextRange> pair : info.quickFixActionRanges) {
        TextRange textRange = pair.second;
        RangeMarker marker = getOrCreate(document, ranges2markersCache, textRange, highlighter);
        info.quickFixActionMarkers.add(Pair.create(pair.first, marker));
      }
    }
    info.fixMarker = getOrCreate(document, ranges2markersCache, new TextRange(info.fixStartOffset, info.fixEndOffset), highlighter);
  }

  private static RangeMarker getOrCreate(Document document, Map<TextRange, RangeMarker> ranges2markersCache, TextRange textRange,
                                         RangeHighlighterEx highlighter) {
    RangeMarker marker = ranges2markersCache.get(textRange);
    if (marker == null && textRange.getStartOffset() == highlighter.getStartOffset() && textRange.getEndOffset() == highlighter.getEndOffset()) {
      marker = highlighter;
    }
    if (marker == null) {
      marker = document.createRangeMarker(textRange);
      ranges2markersCache.put(textRange, marker);
    }
    return marker;
  }

  private static void addFileLevelHighlight(final Project project, final int group, final HighlightInfo info, final PsiFile psiFile) {
    VirtualFile vFile = psiFile.getViewProvider().getVirtualFile();
    final FileEditorManager manager = FileEditorManager.getInstance(project);
    for (FileEditor fileEditor : manager.getEditors(vFile)) {
      if (fileEditor instanceof TextEditor) {
        FileLevelIntentionComponent component = new FileLevelIntentionComponent(info.description, info.severity, info.quickFixActionRanges,
                                                                                project, psiFile, ((TextEditor)fileEditor).getEditor());
        manager.showEditorAnnotation(fileEditor, component);
        List<HighlightInfo> fileLevelInfos = fileEditor.getUserData(FILE_LEVEL_HIGHLIGHTS);
        if (fileLevelInfos == null) {
          fileLevelInfos = new ArrayList<HighlightInfo>();
          fileEditor.putUserData(FILE_LEVEL_HIGHLIGHTS, fileLevelInfos);
        }
        info.fileLevelComponent = component;
        info.group = group;
        fileLevelInfos.add(info);
      }
    }
  }

  public static void setLineMarkersToEditor(@NotNull Project project,
                                            @NotNull Document document,
                                            int startOffset,
                                            int endOffset,
                                            @NotNull Collection<LineMarkerInfo> markers,
                                            int group) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    List<LineMarkerInfo> array = new ArrayList<LineMarkerInfo>();

    List<LineMarkerInfo> oldMarkers = DaemonCodeAnalyzerImpl.getLineMarkers(document, project);
    MarkupModel markupModel = document.getMarkupModel(project);
    if (oldMarkers != null) {
      for (LineMarkerInfo info : oldMarkers) {
        RangeHighlighter highlighter = info.highlighter;
        boolean toRemove = !highlighter.isValid() || info.updatePass == group
                           && startOffset <= highlighter.getStartOffset() && highlighter.getStartOffset() <= endOffset;

        if (toRemove) {
          markupModel.removeHighlighter(highlighter);
        }
        else {
          array.add(info);
        }
      }
      if (LOG.isDebugEnabled()) {
        LOG.debug("Removed line markers:" + (oldMarkers.size() - array.size()));
      }
    }

    for (LineMarkerInfo info : markers) {
      if (startOffset > info.startOffset || info.startOffset > endOffset) continue;
      RangeHighlighter marker = markupModel.addRangeHighlighter(info.startOffset, info.endOffset, HighlighterLayer.ADDITIONAL_SYNTAX,
                                                                info.textAttributes, HighlighterTargetArea.EXACT_RANGE);
      marker.setGutterIconRenderer(info.createGutterRenderer());
      marker.setLineSeparatorColor(info.separatorColor);
      marker.setLineSeparatorPlacement(info.separatorPlacement);
      info.highlighter = marker;
      array.add(info);
    }

    DaemonCodeAnalyzerImpl.setLineMarkers(document, array, project);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Added line markers:" + markers.size());
    }
  }

  private static final Key<Boolean> TYPING_INSIDE_HIGHLIGHTER_OCCURRED = Key.create("TYPING_INSIDE_HIGHLIGHTER_OCCURRED");
  public static boolean isWhitespaceOptimizationAllowed(final Document document) {
    return document.getUserData(TYPING_INSIDE_HIGHLIGHTER_OCCURRED) == null;
  }
  private static void disableWhiteSpaceOptimization(Document document) {
    document.putUserData(TYPING_INSIDE_HIGHLIGHTER_OCCURRED, Boolean.TRUE);
  }
  private static void clearWhiteSpaceOptimizationFlag(final Document document) {
    document.putUserData(TYPING_INSIDE_HIGHLIGHTER_OCCURRED, null);
  }

  public static void updateHighlightersByTyping(@NotNull Project project, @NotNull DocumentEvent e) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    Document document = e.getDocument();
    if (document instanceof DocumentEx && ((DocumentEx)document).isInBulkUpdate()) return;

    List<HighlightInfo> highlights = DaemonCodeAnalyzerImpl.getHighlights(document, project);

    if (highlights == null || highlights.isEmpty()) return;
    int offset = e.getOffset();
    Editor[] editors = EditorFactory.getInstance().getEditors(document, project);
    if (editors.length == 0) return;
    Editor editor = editors[0]; // use any editor - just to fetch SelectInEditorManager
    HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(Math.max(0, offset - 1));
    if (iterator.atEnd()) return;
    int start = iterator.getStart();
    while (iterator.getEnd() < e.getOffset() + e.getNewLength()) {
      iterator.advance();
      if (iterator.atEnd()) return;
    }
    int end = iterator.getEnd();

    List<HighlightInfo> infosToRemove = new ArrayList<HighlightInfo>(highlights.size());
    boolean highlightersChanged = false;
    boolean highlightersRemoved = false;
    List<HighlightInfo> result = new ArrayList<HighlightInfo>(highlights.size());
    boolean documentChangedInsideHighlighter = false;
    for (HighlightInfo info : highlights) {
      RangeHighlighter highlighter = info.highlighter;
      boolean toRemove = false;
      if (info.needUpdateOnTyping()) {
        int highlighterStart = highlighter.getStartOffset();
        int highlighterEnd = highlighter.getEndOffset();
        if (info.isAfterEndOfLine) {
          if (highlighterStart < document.getTextLength()) {
            highlighterStart += 1;
          }
          if (highlighterEnd < document.getTextLength()) {
            highlighterEnd += 1;
          }
        }

        if (!highlighter.isValid()) {
          toRemove = true;
        }
        else if (start < highlighterEnd && highlighterStart < end) {
          LOG.assertTrue(0 <= highlighterStart);
          LOG.assertTrue(highlighterStart < document.getTextLength());
          documentChangedInsideHighlighter = true;
          toRemove = true;
        }
      }

      if (toRemove) {
        if (info.type.equals(HighlightInfoType.WRONG_REF)) {
          document.getMarkupModel(project).removeHighlighter(highlighter);
          highlightersRemoved = true;
        }
        else {
          infosToRemove.add(info);
          result.add(info);
        }
        highlightersChanged = true;
      }
      else {
        result.add(info);
      }
    }

    if (highlightersRemoved) {
      DaemonCodeAnalyzerImpl.setHighlights(document, result, project);
    }
    if (highlightersChanged || documentChangedInsideHighlighter) {
      disableWhiteSpaceOptimization(document);
    }
    if (highlightersChanged) {
      setHighlightsToRemove(infosToRemove, document.getMarkupModel(project));
    }
  }

  private static final Key<List<HighlightInfo>> HIGHLIGHTS_TO_REMOVE_KEY = Key.create("HIGHLIGHTS_TO_REMOVE");
  private static void setHighlightsToRemove(List<HighlightInfo> highlights, MarkupModel markup) {
    markup.putUserData(HIGHLIGHTS_TO_REMOVE_KEY, highlights);
  }
  @NotNull
  private static List<HighlightInfo> getHighlightsToRemove(MarkupModel markup) {
    List<HighlightInfo> infos = markup.getUserData(HIGHLIGHTS_TO_REMOVE_KEY);
    return infos == null ? Collections.<HighlightInfo>emptyList() : infos;
  }
}
