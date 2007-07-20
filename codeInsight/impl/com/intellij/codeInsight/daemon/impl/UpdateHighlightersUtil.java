package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
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
import com.intellij.openapi.editor.ex.HighlighterIterator;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class UpdateHighlightersUtil {

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil");

  private static final Key<List<HighlightInfo>> FILE_LEVEL_HIGHLIGHTS = Key.create("FILE_LEVEL_HIGHLIGHTS");

  private UpdateHighlightersUtil() {}

  private static void cleanFileLevelHighlights(@NotNull Project project, @NotNull Document document, final int group) {
    final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
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
    setAndGetHighlightersToEditor(project, document, Collections.singletonMap(new TextRange(startOffset, endOffset), highlights), group);
  }

  public static HighlightInfo[] setAndGetHighlightersToEditor(@NotNull Project project,
                                                              @NotNull Document document,
                                                              @NotNull Map<TextRange, Collection<HighlightInfo>> infos,
                                                              final int group) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    //serialized implicitly by the dispatch thread
                              
    cleanFileLevelHighlights(project, document, group);

    List<TextRange> ranges = new ArrayList<TextRange>(infos.keySet());
    Collections.sort(ranges, new Comparator<TextRange>() {
      public int compare(final TextRange o1, final TextRange o2) {
        return o1.getStartOffset() - o2.getStartOffset();
      }
    });
    //merge enclosing
    TextRange prev = ranges.get(0);
    for (int i = 1; i < ranges.size(); i++) {
      TextRange range = ranges.get(i);
      if (prev.contains(range)) {
        TextRange removed = ranges.remove(i);
        infos.get(prev).addAll(infos.get(removed));
        i--;
      }
    }

    HighlightInfo[] oldHighlights = DaemonCodeAnalyzerImpl.getHighlights(document, project);
    List<HighlightInfo> result = new ArrayList<HighlightInfo>();
    if (oldHighlights != null) {
      for (HighlightInfo info : oldHighlights) {
        RangeHighlighter highlighter = info.highlighter;
        boolean toRemove = !highlighter.isValid() ||
                           info.group == group && Collections.binarySearch(ranges, new TextRange(highlighter.getStartOffset(), highlighter.getEndOffset()), new Comparator<TextRange>() {
                             public int compare(final TextRange o1, final TextRange o2) {
                               if (o1.contains(o2) || o2.contains(o1)) return 0;
                               return o1.getStartOffset() - o2.getStartOffset();
                             }
                           }) >= 0;
        if (toRemove) {
          document.getMarkupModel(project).removeHighlighter(highlighter);
        }
        else {
          result.add(info);
        }
      }
      if (LOG.isDebugEnabled()) {
        LOG.debug("Removed segment highlighters:" + (oldHighlights.length - result.size()));
      }
    }

    for (TextRange range : ranges) {
      Collection<HighlightInfo> highlights = infos.get(range);
      int startOffset = range.getStartOffset();
      int endOffset = range.getEndOffset();

      for (HighlightInfo info : highlights) {
        int infoEndOffset = info.endOffset;
        int infoStartOffset = info.startOffset;

        if (infoStartOffset < startOffset || infoEndOffset > endOffset) continue;
        HighlightSeverity severity = info.getSeverity();
        int layer;
        if (severity == HighlightSeverity.INFORMATION || severity == HighlightSeverity.INFO || severity == HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING) {
          layer = HighlighterLayer.ADDITIONAL_SYNTAX;
        }
        else if (severity == HighlightSeverity.WARNING) {
          layer = HighlighterLayer.WARNING;
        }
        else {
          layer = HighlighterLayer.ERROR;
        }

        final int docLength = document.getTextLength();
        if (infoEndOffset > docLength) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Invalid HighlightInfo created: (" + infoStartOffset + ":" + infoEndOffset + ")" + info.description);
          }

          infoEndOffset = docLength;
        }

        if (info.isFileLevelAnnotation) {
          final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
          if (psiFile != null) {
            if (psiFile.getViewProvider().isPhysical()) {
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

              continue;
            }
          }
        }

        RangeHighlighterEx highlighter = (RangeHighlighterEx)document.getMarkupModel(project).addRangeHighlighter(
          infoStartOffset,
          infoEndOffset,
          layer,
          info.getTextAttributes(),
          HighlighterTargetArea.EXACT_RANGE);

        //highlighter.setUserObject(info); // debug purposes only!
        info.highlighter = highlighter;
        highlighter.setAfterEndOfLine(info.isAfterEndOfLine);
        info.text = document.getCharsSequence().subSequence(infoStartOffset, infoEndOffset).toString();
        info.group = group;

        highlighter.setErrorStripeMarkColor(info.getErrorStripeMarkColor());
        highlighter.setErrorStripeTooltip(info);
        highlighter.setGutterIconRenderer(info.getGutterIconRenderer());

        HashMap<TextRange, RangeMarker> ranges2markers = new HashMap<TextRange, RangeMarker>();
        ranges2markers.put(new TextRange(infoStartOffset, infoEndOffset), info.highlighter);
        if (info.quickFixActionRanges != null) {
          info.quickFixActionMarkers = new ArrayList<Pair<HighlightInfo.IntentionActionDescriptor, RangeMarker>>();
          for (Pair<HighlightInfo.IntentionActionDescriptor, TextRange> pair : info.quickFixActionRanges) {
            TextRange textRange = pair.second;
            RangeMarker marker = ranges2markers.get(textRange);
            if (marker == null) {
              marker = document.createRangeMarker(textRange.getStartOffset(), textRange.getEndOffset());
              ranges2markers.put(textRange, marker);
            }
            info.quickFixActionMarkers.add(Pair.create(pair.first, marker));
          }
        }
        info.fixMarker = ranges2markers.get(new TextRange(info.fixStartOffset, info.fixEndOffset));
        if (info.fixMarker == null) {
          info.fixMarker = document.createRangeMarker(info.fixStartOffset, info.fixEndOffset);
        }

        result.add(info);
      }
    }

    HighlightInfo[] newHighlights = result.toArray(new HighlightInfo[result.size()]);
    DaemonCodeAnalyzerImpl.setHighlights(document, newHighlights, project);
    clearWhiteSpaceOptimizationFlag(document);
    return newHighlights;
  }

  public static void setLineMarkersToEditor(@NotNull Project project,
                                            @NotNull Document document,
                                            int startOffset,
                                            int endOffset,
                                            @NotNull Collection<LineMarkerInfo> markers,
                                            int group) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    List<LineMarkerInfo> array = new ArrayList<LineMarkerInfo>();

    LineMarkerInfo[] oldMarkers = DaemonCodeAnalyzerImpl.getLineMarkers(document, project);
    if (oldMarkers != null) {
      for (LineMarkerInfo info : oldMarkers) {
        RangeHighlighter highlighter = info.highlighter;
        boolean toRemove = !highlighter.isValid() ||
                           isLineMarkerInGroup(info.type, group) && startOffset <= highlighter.getStartOffset() && highlighter.getStartOffset() <= endOffset;

        if (toRemove) {
          document.getMarkupModel(project).removeHighlighter(highlighter);
        }
        else {
          array.add(info);
        }
      }
      if (LOG.isDebugEnabled()) {
        LOG.debug("Removed line markers:" + (oldMarkers.length - array.size()));
      }
    }

    for (LineMarkerInfo info : markers) {
      RangeHighlighter marker = document.getMarkupModel(project).addRangeHighlighter(info.startOffset,
                                                                                     info.startOffset,
                                                                                     HighlighterLayer.ADDITIONAL_SYNTAX,
                                                                                     info.attributes,
                                                                                     HighlighterTargetArea.EXACT_RANGE);
      marker.setGutterIconRenderer(info.createGutterRenderer());
      marker.setLineSeparatorColor(info.separatorColor);
      marker.setLineSeparatorPlacement(info.separatorPlacement);
      info.highlighter = marker;
      array.add(info);
    }

    LineMarkerInfo[] newMarkers = array.toArray(new LineMarkerInfo[array.size()]);
    DaemonCodeAnalyzerImpl.setLineMarkers(document, newMarkers, project);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Added line markers:" + markers.size());
    }
  }

  private static boolean isLineMarkerInGroup(LineMarkerInfo.MarkerType type, int group) {
    switch (type) {
      case OVERRIDEN_METHOD:
      case SUBCLASSED_CLASS:
        return group == Pass.UPDATE_OVERRIDEN_MARKERS;

      case OVERRIDING_METHOD:
        /*
        return true; // in both groups

        */
      case METHOD_SEPARATOR:
        return group == Pass.UPDATE_ALL;

      default:
        LOG.assertTrue(false);
        return false;
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
    Document document = e.getDocument();
    if (document instanceof DocumentEx && ((DocumentEx)document).isInBulkUpdate()) return;

    HighlightInfo[] highlights = DaemonCodeAnalyzerImpl.getHighlights(document, project);

    if (highlights == null || highlights.length == 0) {
      return;
    }
    int offset = e.getOffset();
    Editor[] editors = EditorFactory.getInstance().getEditors(document, project);
    if (editors.length == 0) {
      return;
    }
    Editor editor = editors[0]; // use any editor - just to fetch SelectInEditorManager
    HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(Math.max(0, offset - 1));
    if (iterator.atEnd()) return;
    int start = iterator.getStart();
    while (iterator.getEnd() < e.getOffset() + e.getNewLength()) {
      iterator.advance();
      if (iterator.atEnd()) return;
    }
    int end = iterator.getEnd();

    List<HighlightInfo> array = new ArrayList<HighlightInfo>(highlights.length);
    boolean changes = false;
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
          HighlighterIterator iterator1 = ((EditorEx)editor).getHighlighter().createIterator(highlighterStart);
          int start1 = iterator1.getStart();
          while (iterator1.getEnd() < highlighterEnd) {
            iterator1.advance();
          }
          int end1 = iterator1.getEnd();
          CharSequence chars = document.getCharsSequence();
          String token = chars.subSequence(start1, end1).toString();
          if (start1 != highlighterStart || end1 != highlighterEnd || !token.equals(info.text)) {
            toRemove = true;
            disableWhiteSpaceOptimization(document);
          }
        }
      }

      if (toRemove) {
        document.getMarkupModel(project).removeHighlighter(highlighter);
        changes = true;
      }
      else {
        array.add(info);
      }
    }

    if (changes) {
      HighlightInfo[] newHighlights = array.toArray(new HighlightInfo[array.size()]);
      DaemonCodeAnalyzerImpl.setHighlights(document, newHighlights, project);
    }
  }

  /*
  // temp!!
  public static void checkConsistency(DaemonCodeAnalyzerImpl codeAnalyzer, Document document){
    LOG.assertTrue(ApplicationImpl.isDispatchThreadStatic());
    HighlightInfo[] highlights = codeAnalyzer.getHighlights(document);
    if (highlights == null){
      highlights = new HighlightInfo[0];
    }
    ArrayList infos = new ArrayList();
    for(int i = 0; i < highlights.length; i++){
      infos.add(highlights[i]);
    }

    RangeHighlighter[] highlighters = document.getMarkupModel().getAllHighlighters for(int i = 0; i < highlighters.length; i++){
      RangeHighlighter highlighter = highlighters[i];
      Object userObject = ((RangeHighlighterEx)highlighter).getUserObject();
      if (userObject instanceof HighlightInfo){
        LOG.assertTrue(infos.contains(userObject));
        infos.remove(userObject);
      }
    }
    LOG.assertTrue(infos.isEmpty());
  }
  */

}