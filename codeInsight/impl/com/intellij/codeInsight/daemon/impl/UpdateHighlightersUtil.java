package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.FileLevelIntentionComponent;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.event.DocumentEvent;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class UpdateHighlightersUtil {

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil");

  public static final int NORMAL_HIGHLIGHTERS_GROUP = 1;
  public static final int POST_HIGHLIGHTERS_GROUP = 2;
  public static final int INSPECTION_HIGHLIGHTERS_GROUP = 3;
  public static final int EXTERNAL_TOOLS_HIGHLIGHTERS_GROUP = 4;
  public static final int[] POST_HIGHLIGHT_GROUPS = new int[]{POST_HIGHLIGHTERS_GROUP, INSPECTION_HIGHLIGHTERS_GROUP, EXTERNAL_TOOLS_HIGHLIGHTERS_GROUP};
  public static final int[] NORMAL_HIGHLIGHT_GROUPS = new int[]{NORMAL_HIGHLIGHTERS_GROUP};

  private static final Key<List<HighlightInfo>> FILE_LEVEL_HIGHLIGHTS = Key.create("FILE_LEVEL_HIGHLIGHTS");

  private UpdateHighlightersUtil() {}

  private static void cleanFileLevelHighlights(Project project, Document document, final int group) {
    final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (psiFile != null) {
      if (psiFile.getViewProvider().isPhysical()) {
        VirtualFile vFile = psiFile.getViewProvider().getVirtualFile();
        final FileEditorManager manager = FileEditorManager.getInstance(project);
        for (FileEditor fileEditor : manager.getEditors(vFile)) {
          final List<HighlightInfo> infos = fileEditor.getUserData(FILE_LEVEL_HIGHLIGHTS);
          if (infos != null) {
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
      }
    }

  }

  public static void setHighlightersToEditor(Project project,
                                             Document document,
                                             int startOffset,
                                             int endOffset,
                                             Collection<HighlightInfo> highlights,
                                             int group) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    List<HighlightInfo> array = new ArrayList<HighlightInfo>();

    cleanFileLevelHighlights(project, document, group);

    HighlightInfo[] oldHighlights = DaemonCodeAnalyzerImpl.getHighlights(document, project);

    if (oldHighlights != null) {
      for (HighlightInfo info : oldHighlights) {
        RangeHighlighter highlighter = info.highlighter;
        boolean toRemove;
        toRemove = !highlighter.isValid() ||
                   info.group == group && startOffset <= highlighter.getStartOffset() && highlighter.getEndOffset() <= endOffset;

        if (toRemove) {
          document.getMarkupModel(project).removeHighlighter(highlighter);
        }
        else {
          array.add(info);
        }
      }
      if (LOG.isDebugEnabled()) {
        LOG.debug("Removed segment highlighters:" + (oldHighlights.length - array.size()));
      }
    }

    for (HighlightInfo info : highlights) {
      int layer;
      int infoEndOffset = info.endOffset;
      int infoStartOffset = info.startOffset;

      if (infoStartOffset < startOffset || infoEndOffset > endOffset) continue;
      HighlightSeverity severity = info.getSeverity();
      if (severity == HighlightSeverity.INFORMATION) {
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
                                                                                        project, ((TextEditor)fileEditor).getEditor());
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
        info.quickFixActionMarkers = new ArrayList<Pair<Pair<Pair<IntentionAction,String>, List<IntentionAction>>, RangeMarker>>();
        for (Pair<Pair<Pair<IntentionAction,String>,List<IntentionAction>>,TextRange> pair : info.quickFixActionRanges) {
          TextRange range = pair.second;
          RangeMarker marker = ranges2markers.get(range);
          if (marker == null) {
            marker = document.createRangeMarker(range.getStartOffset(), range.getEndOffset());
            ranges2markers.put(range, marker);
          }
          info.quickFixActionMarkers.add(Pair.create(pair.first, marker));
        }
      }
      info.fixMarker = ranges2markers.get(new TextRange(info.fixStartOffset, info.fixEndOffset));
      if (info.fixMarker == null) {
        info.fixMarker = document.createRangeMarker(info.fixStartOffset, info.fixEndOffset);
      }

      array.add(info);
    }

    HighlightInfo[] newHighlights = array.toArray(new HighlightInfo[array.size()]);
    DaemonCodeAnalyzerImpl.setHighlights(document, newHighlights, project);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Added segment highlighters:" + highlights.size());
    }
  }

  private static List<IntentionAction> getQuickFixes(final HighlightInfo info) {
    if (info.quickFixActionRanges != null) {
      List<IntentionAction> actions = new ArrayList<IntentionAction>();
      for (Pair<Pair<Pair<IntentionAction, String>, List<IntentionAction>>, TextRange> pair : info.quickFixActionRanges) {
        actions.add(pair.getFirst().getFirst().getFirst());
      }
      return actions;
    }
    return Collections.emptyList();
  }

  public static final int NORMAL_MARKERS_GROUP = 1;
  public static final int OVERRIDEN_MARKERS_GROUP = 2;

  public static void setLineMarkersToEditor(Project project,
                                            Document document,
                                            int startOffset,
                                            int endOffset,
                                            Collection<LineMarkerInfo> markers,
                                            int group) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    ArrayList<LineMarkerInfo> array = new ArrayList<LineMarkerInfo>();

    LineMarkerInfo[] oldMarkers = DaemonCodeAnalyzerImpl.getLineMarkers(document, project);
    if (oldMarkers != null) {
      for (LineMarkerInfo info : oldMarkers) {
        RangeHighlighter highlighter = info.highlighter;
        boolean toRemove;
        if (!highlighter.isValid()) {
          toRemove = true;
        }
        else {
          toRemove = isLineMarkerInGroup(info.type, group)
                     && startOffset <= highlighter.getStartOffset() && highlighter.getStartOffset() <= endOffset;
        }

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
                                                                                     HighlighterTargetArea.LINES_IN_RANGE);
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

  private static boolean isLineMarkerInGroup(int type, int group) {
    switch (type) {
      case LineMarkerInfo.OVERRIDEN_METHOD:
      case LineMarkerInfo.SUBCLASSED_CLASS:
        return group == OVERRIDEN_MARKERS_GROUP;

      case LineMarkerInfo.OVERRIDING_METHOD:
        /*
        return true; // in both groups

        */
      case LineMarkerInfo.METHOD_SEPARATOR:
        return group == NORMAL_MARKERS_GROUP;

      default:
        LOG.assertTrue(false);
        return false;
    }
  }

  public static void updateHighlightersByTyping(Project project, DocumentEvent e) {
    Document document = e.getDocument();

    HighlightInfo[] highlights = DaemonCodeAnalyzerImpl.getHighlights(document, project);
    if (highlights != null) {
      int offset = e.getOffset();
      Editor[] editors = EditorFactory.getInstance().getEditors(document, project);
      if (editors.length > 0) {
        Editor editor = editors[0]; // use any editor - just to fetch SelectInEditorManager
        HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(Math.max(0, offset - 1));
        if (iterator.atEnd()) return;
        int start = iterator.getStart();
        while (iterator.getEnd() < e.getOffset() + e.getNewLength()) {
          iterator.advance();
          if (iterator.atEnd()) return;
        }
        int end = iterator.getEnd();

        ArrayList<HighlightInfo> array = new ArrayList<HighlightInfo>();
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