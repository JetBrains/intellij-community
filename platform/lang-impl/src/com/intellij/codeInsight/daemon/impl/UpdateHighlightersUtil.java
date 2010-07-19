/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.intention.impl.FileLevelIntentionComponent;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.impl.RangeHighlighterImpl;
import com.intellij.openapi.editor.markup.*;
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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.awt.*;
import java.util.*;
import java.util.List;

public class UpdateHighlightersUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil");
  private static final boolean DEBUG = LOG.isDebugEnabled();

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
          manager.removeTopComponent(fileEditor, info.fileLevelComponent);
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

  static boolean hasInfo(Collection<HighlightInfo> infos, int start, int end, String desc) {
    if (infos == null) return false;
    for (HighlightInfo info : infos) {
      if (info.startOffset == start && info.endOffset == end && info.description.equals(desc)) return true;
    }
    return false;
  }

  private static class RangeHighlightersToReuse {
    private final MultiMap<TextRange, RangeHighlighter> info2Pass = new MultiMap<TextRange, RangeHighlighter>(){
      @Override
      protected Map<TextRange, Collection<RangeHighlighter>> createMap() {
        return new THashMap<TextRange, Collection<RangeHighlighter>>();
      }

      @Override
      protected Collection<RangeHighlighter> createCollection() {
        return new SmartList<RangeHighlighter>();
      }
    };

    void add(RangeHighlighter highlighter) {
      info2Pass.putValue(InjectedLanguageUtil.toTextRange(highlighter), highlighter);
    }

    RangeHighlighter reuseHighlighterAt(int startOffset, int endOffset){
      TextRange range = new TextRange(startOffset, endOffset);
      Collection<RangeHighlighter> collection = info2Pass.get(range);
      for (RangeHighlighter highlighter : collection) {
        if (!highlighter.isValid()) continue;
        info2Pass.removeValue(range, highlighter);
        return highlighter;
      }
      return null;
    }
    Collection<? extends RangeHighlighter> forAll() {
      return info2Pass.values();
    }
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

    MarkupModel markup = document.getMarkupModel(project);
    List<HighlightInfo> oldHighlights = DaemonCodeAnalyzerImpl.getHighlights(markup);
    List<HighlightInfo> oldHighlightsToRemove = DaemonCodeAnalyzerImpl.getHighlightsToRemove(markup);
    assertMarkupConsistent(markup, oldHighlights, oldHighlightsToRemove);

    List<HighlightInfo> result = new ArrayList<HighlightInfo>();
    RangeHighlightersToReuse infosToRemove = new RangeHighlightersToReuse();

    boolean changed = false;
    List<HighlightInfo> toRemoveInAnotherPass = new SmartList<HighlightInfo>();

    if (oldHighlights != null) {
      List<HighlightInfo> list = ContainerUtil.concat(oldHighlightsToRemove, oldHighlights);
      for (int i = 0; i < list.size(); i++) {
        HighlightInfo info = list.get(i);
        RangeHighlighter highlighter = info.highlighter;
        boolean toRemove = isRemoving(group, ranges, info, highlighter);
        if (toRemove) {
          infosToRemove.add(highlighter);
          changed = true;
        }
        else if (i >= oldHighlightsToRemove.size()) {
          result.add(info);
        }
        else {
          toRemoveInAnotherPass.add(info);
        }
      }
    }

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

        if (infoEndOffset == infoStartOffset && !info.isAfterEndOfLine) {
          infoEndOffset++; //show something in case of empty highlightinfo
        }
        final int docLength = document.getTextLength();
        if (infoEndOffset > docLength) {
          infoEndOffset = docLength;
        }

        info.text = document.getCharsSequence().subSequence(infoStartOffset, infoEndOffset).toString();
        info.group = group;


        RangeHighlighterEx highlighter = (RangeHighlighterEx)infosToRemove.reuseHighlighterAt(info.startOffset, info.endOffset);
        if (highlighter == null) {
          highlighter = createRangeHighlighter(project, psiFile, info, infoStartOffset, infoEndOffset, markup);
        }
        else {
          ((RangeHighlighterImpl)highlighter).setTextAttributes(info.getTextAttributes(psiFile));
        }
        changed = true;
        info.highlighter = highlighter;
        highlighter.setAfterEndOfLine(info.isAfterEndOfLine);

        Color color = info.getErrorStripeMarkColor(psiFile);
        highlighter.setErrorStripeMarkColor(color);
        if (!(highlighter.getErrorStripeTooltip() instanceof HighlightInfo) || !info.equalsByActualOffset((HighlightInfo)highlighter.getErrorStripeTooltip())) {
          highlighter.setErrorStripeTooltip(info);
        }
        GutterIconRenderer renderer = info.getGutterIconRenderer();
        highlighter.setGutterIconRenderer(renderer);

        ranges2markersCache.put(new TextRange(infoStartOffset, infoEndOffset), info.highlighter);
        if (info.quickFixActionRanges != null) {
          info.quickFixActionMarkers = new ArrayList<Pair<HighlightInfo.IntentionActionDescriptor, RangeMarker>>(info.quickFixActionRanges.size());
          for (Pair<HighlightInfo.IntentionActionDescriptor, TextRange> pair : info.quickFixActionRanges) {
            TextRange textRange = pair.second;
            RangeMarker marker = getOrCreate(document, ranges2markersCache, textRange);
            info.quickFixActionMarkers.add(Pair.create(pair.first, marker));
          }
        }
        info.fixMarker = getOrCreate(document, ranges2markersCache, new TextRange(info.fixStartOffset, info.fixEndOffset));

        assert Comparing.equal(info.getTextAttributes(psiFile), highlighter.getTextAttributes()) : "Info: " +
                                                                                                   info.getTextAttributes(psiFile) +
                                                                                                   "; highlighter:" +
                                                                                                   highlighter.getTextAttributes();

        result.add(info);
      }
    }

    for (RangeHighlighter highlighter : infosToRemove.forAll()) {
      markup.removeHighlighter(highlighter);
      changed = true;
    }

    if (changed) {
      DaemonCodeAnalyzerImpl.setHighlights(markup, project, result, toRemoveInAnotherPass);
      clearWhiteSpaceOptimizationFlag(document);
    }
    assertMarkupConsistent(markup, result, toRemoveInAnotherPass);
  }

  private static boolean isRemoving(int group, List<TextRange> ranges, HighlightInfo info, RangeHighlighter highlighter) {
    if (!highlighter.isValid()) return true;
    if (info.group != group) return false;
    TextRange toFind = new TextRange(highlighter.getStartOffset(), highlighter.getEndOffset());
    int i = Collections.binarySearch(ranges, toFind, BY_START_OFFSET_OR_CONTAINS);
    if (i<0) return false;
    Document document = highlighter.getDocument();
    if (highlighter.getEndOffset() == document.getTextLength() && ranges.get(ranges.size()-1).getEndOffset() == document.getTextLength()) {
      return true;
    }
    TextRange containing = ranges.get(i);
    return containing.contains(toFind.getStartOffset());
  }

  private static RangeHighlighterEx createRangeHighlighter(Project project,
                                                           PsiFile psiFile,
                                                           HighlightInfo info,
                                                           int infoStartOffset,
                                                           int infoEndOffset,
                                                           MarkupModel markup) {
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

    return (RangeHighlighterEx)markup.addRangeHighlighter(infoStartOffset, infoEndOffset, layer, info.getTextAttributes(psiFile), HighlighterTargetArea.EXACT_RANGE);
  }

  private static RangeMarker getOrCreate(Document document, Map<TextRange, RangeMarker> ranges2markersCache, TextRange textRange) {
    RangeMarker marker = ranges2markersCache.get(textRange);
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
        manager.addTopComponent(fileEditor, component);
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

    List<LineMarkerInfo> oldMarkers = DaemonCodeAnalyzerImpl.getLineMarkers(document, project);
    List<LineMarkerInfo> array = new ArrayList<LineMarkerInfo>(oldMarkers == null ? markers.size() : oldMarkers.size());
    MarkupModel markupModel = document.getMarkupModel(project);
    RangeHighlightersToReuse toReuse = new RangeHighlightersToReuse();
    if (oldMarkers != null) {
      for (LineMarkerInfo info : oldMarkers) {
        RangeHighlighter highlighter = info.highlighter;
        boolean toRemove = !highlighter.isValid() ||
                           info.updatePass == group &&
                           startOffset <= highlighter.getStartOffset() &&
                           (highlighter.getEndOffset() < endOffset || highlighter.getEndOffset() == document.getTextLength());

        if (toRemove) {
          toReuse.add(highlighter);
        }
        else {
          array.add(info);
        }
      }
    }

    final EditorColorsScheme colorsScheme = EditorColorsManager.getInstance().getGlobalScheme(); // TODO: editor color scheme
    for (LineMarkerInfo info : markers) {
      PsiElement element = info.getElement();
      if (element == null) {
        continue;
      }

      TextRange textRange = element.getTextRange();
      if (textRange == null) continue;
      TextRange elementRange = InjectedLanguageManager.getInstance(project).injectedToHost(element, textRange);
      if (startOffset > elementRange.getStartOffset() || elementRange.getEndOffset() > endOffset) {
        continue;
      }
      RangeHighlighter marker = toReuse.reuseHighlighterAt(info.startOffset, info.endOffset);
      if (marker == null) {
        marker = markupModel.addRangeHighlighter(info.startOffset, info.endOffset, HighlighterLayer.ADDITIONAL_SYNTAX, null, HighlighterTargetArea.EXACT_RANGE);
      }
      LineMarkerInfo.LineMarkerGutterIconRenderer renderer = (LineMarkerInfo.LineMarkerGutterIconRenderer)info.createGutterRenderer();
      LineMarkerInfo.LineMarkerGutterIconRenderer oldRenderer = marker.getGutterIconRenderer() instanceof LineMarkerInfo.LineMarkerGutterIconRenderer ? (LineMarkerInfo.LineMarkerGutterIconRenderer)marker.getGutterIconRenderer() : null;
      if (oldRenderer == null || renderer == null || !renderer.looksTheSameAs(oldRenderer)) {
        marker.setGutterIconRenderer(renderer);
      }
      if (!Comparing.equal(marker.getLineSeparatorColor(), info.separatorColor)) {
        marker.setLineSeparatorColor(info.separatorColor);
      }
      if (!Comparing.equal(marker.getLineSeparatorPlacement(), info.separatorPlacement)) {
        marker.setLineSeparatorPlacement(info.separatorPlacement);
      }
      info.highlighter = marker;
      array.add(info);
    }

    for (RangeHighlighter highlighter : toReuse.forAll()) {
      markupModel.removeHighlighter(highlighter);
    }

    DaemonCodeAnalyzerImpl.setLineMarkers(document, array, project);
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

    MarkupModel markup = document.getMarkupModel(project);
    List<HighlightInfo> oldHighlights = DaemonCodeAnalyzerImpl.getHighlights(markup);
    if (oldHighlights == null || oldHighlights.isEmpty()) return;

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

    List<HighlightInfo> infosToRemove = new ArrayList<HighlightInfo>(DaemonCodeAnalyzerImpl.getHighlightsToRemove(markup));
    boolean highlightersChanged = false;
    List<HighlightInfo> result = new ArrayList<HighlightInfo>(oldHighlights.size());
    boolean documentChangedInsideHighlighter = false;
    for (HighlightInfo info : oldHighlights) {
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
        else if (start < highlighterEnd && highlighterStart <= end) {
          documentChangedInsideHighlighter = true;
          toRemove = true;
        }
      }

      if (toRemove) {
        if (info.type.equals(HighlightInfoType.WRONG_REF)) {
          markup.removeHighlighter(highlighter);
        }
        else {
          infosToRemove.add(info);
        }
        highlightersChanged = true;
      }
      else {
        result.add(info);
      }
    }

    if (highlightersChanged) {
      DaemonCodeAnalyzerImpl.setHighlights(markup, project, result, infosToRemove);
    }
    assertMarkupConsistent(markup, result, infosToRemove);

    if (highlightersChanged || documentChangedInsideHighlighter) {
      disableWhiteSpaceOptimization(document);
    }
  }

  @NotNull
  @TestOnly
  public static List<HighlightInfo> getFileLeveleHighlights(Project project, PsiFile file) {
    VirtualFile vFile = file.getViewProvider().getVirtualFile();
    final FileEditorManager manager = FileEditorManager.getInstance(project);
    List<HighlightInfo> result = new ArrayList<HighlightInfo>();
    for (FileEditor fileEditor : manager.getEditors(vFile)) {
      final List<HighlightInfo> infos = fileEditor.getUserData(FILE_LEVEL_HIGHLIGHTS);
      if (infos == null) continue;
      for (HighlightInfo info : infos) {
          result.add(info);
      }
    }
    return result;
  }

  private static void assertMarkupConsistent(MarkupModel markup, List<HighlightInfo> highlightsToSet, List<HighlightInfo> highlightsToRemove) {
    if (DEBUG) { // TODO: [cdr] the checks are quadratic to number of highlights,
                 // PLEASE make them more efficient if there is a need to switch them on,
                 //  there are many problematic tests FAILING for this reason!
      if (highlightsToSet != null) {
        for (HighlightInfo info : highlightsToSet) {
          assert ((MarkupModelEx)markup).containsHighlighter(info.highlighter);
        }
      }
      RangeHighlighter[] allHighlighters = markup.getAllHighlighters();
      for (RangeHighlighter highlighter : allHighlighters) {
        Object tooltip = highlighter.getErrorStripeTooltip();
        if (tooltip instanceof HighlightInfo) {
          HighlightInfo info = (HighlightInfo)tooltip;
          assert highlightsToSet != null && highlightsToSet.contains(info)
                 || highlightsToRemove != null && highlightsToRemove.contains(info)
            ;
        }
      }
    }
  }
}
