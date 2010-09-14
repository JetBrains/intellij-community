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
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.impl.RangeMarkerTree;
import com.intellij.openapi.editor.impl.RedBlackTree;
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
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class UpdateHighlightersUtil {
  private static final Comparator<HighlightInfo> BY_START_OFFSET_NODUPS = new Comparator<HighlightInfo>() {
    public int compare(HighlightInfo o1, HighlightInfo o2) {
      int d = o1.getActualStartOffset() - o2.getActualStartOffset();
      if (d != 0) return d;
      d = o1.getActualEndOffset() - o2.getActualEndOffset();
      if (d != 0) return d;

      d = Comparing.compare(o1.getSeverity(), o2.getSeverity());
      if (d != 0) return -d; // higher severity first, to prevent warnings overlap errors

      if (!Comparing.equal(o1.type, o2.type)) {
        return String.valueOf(o1.type).compareTo(String.valueOf(o2.type));
      }

      if (!Comparing.equal(o1.getGutterIconRenderer(), o2.getGutterIconRenderer())) {
        return String.valueOf(o1.getGutterIconRenderer()).compareTo(String.valueOf(o2.getGutterIconRenderer()));
      }

      if (!Comparing.equal(o1.forcedTextAttributes, o2.forcedTextAttributes)) {
        return String.valueOf(o1.getGutterIconRenderer()).compareTo(String.valueOf(o2.getGutterIconRenderer()));
      }

      return Comparing.compare(o1.description, o2.description);
    }
  };

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

  private static class HighlightersRecycler {
    private final MultiMap<TextRange, RangeHighlighter> incinerator = new MultiMap<TextRange, RangeHighlighter>(){
      @Override
      protected Map<TextRange, Collection<RangeHighlighter>> createMap() {
        return new THashMap<TextRange, Collection<RangeHighlighter>>();
      }

      @Override
      protected Collection<RangeHighlighter> createCollection() {
        return new SmartList<RangeHighlighter>();
      }
    };

    void recycleHighlighter(RangeHighlighter highlighter) {
      incinerator.putValue(InjectedLanguageUtil.toTextRange(highlighter), highlighter);
    }

    RangeHighlighter pickupHighlighterFromGarbageBin(int startOffset, int endOffset, int layer){
      TextRange range = new TextRange(startOffset, endOffset);
      Collection<RangeHighlighter> collection = incinerator.get(range);
      for (RangeHighlighter highlighter : collection) {
        if (highlighter.isValid() && highlighter.getLayer() == layer) {
          incinerator.removeValue(range, highlighter);
          return highlighter;
        }
      }
      return null;
    }
    Collection<? extends RangeHighlighter> forAllInGarbageBin() {
      return incinerator.values();
    }
  }

  public static void addHighlighterToEditorIncrementally(@NotNull Project project,
                                                         @NotNull Document document,
                                                         @NotNull PsiFile file,
                                                         int startOffset,
                                                         int endOffset,
                                                         @NotNull final HighlightInfo info,
                                                         final int group) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (info.isFileLevelAnnotation) return;

    MarkupModel markup = document.getMarkupModel(project);
    Processor<HighlightInfo> otherHighlightInTheWayProcessor = new Processor<HighlightInfo>() {
      public boolean process(HighlightInfo oldInfo) {
        return oldInfo.group != group || !oldInfo.equalsByActualOffset(info);
      }
    };
    if (!DaemonCodeAnalyzerImpl.processHighlights(document, project,
                                                  null, info.getActualStartOffset(), info.getActualEndOffset(),
                                                  otherHighlightInTheWayProcessor)) {
      return;
    }

    boolean success = createOrReuseHighlighterFor(info, document, group, file, (MarkupModelEx)markup, null, null, startOffset, endOffset,
                                                  SeverityRegistrar.getInstance(project));
    if (!success) {
      return;
    }

    DaemonCodeAnalyzerImpl.addHighlight(markup, project, info);
    clearWhiteSpaceOptimizationFlag(document);
    assertMarkupConsistent(markup, project);
  }

  public static void setHighlightersToEditor(@NotNull Project project,
                                             @NotNull Document document,
                                             @NotNull Map<TextRange, Collection<HighlightInfo>> infos,
                                             final int group) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
    cleanFileLevelHighlights(project, group, psiFile);

    final List<TextRange> ranges = new ArrayList<TextRange>(infos.keySet());
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
    assertMarkupConsistent(markup, project);

    for (Map.Entry<TextRange, Collection<HighlightInfo>> entry : infos.entrySet()) {
      TextRange range = entry.getKey();
      Collection<HighlightInfo> highlights = entry.getValue();
      setHighlightersInRange(range, highlights, (MarkupModelEx)markup, group, document, project);
    }
  }

  private static void setHighlightersInRange(final TextRange range,
                                             Collection<HighlightInfo> highlightsCo,
                                             final MarkupModelEx markup,
                                             final int group,
                                             final Document document,
                                             final Project project) {
    final List<HighlightInfo> highlights = new ArrayList<HighlightInfo>(highlightsCo);

    final SeverityRegistrar severityRegistrar = SeverityRegistrar.getInstance(project);
    final HighlightersRecycler infosToRemove = new HighlightersRecycler();
    DaemonCodeAnalyzerImpl.processHighlights(document, project, null, range.getStartOffset(), range.getEndOffset(), new Processor<HighlightInfo>() {
      @Override
      public boolean process(HighlightInfo info) {
        if (info.group == group) {
          RangeHighlighter highlighter = info.highlighter;
          int endOffset = highlighter.getEndOffset();
          int startOffset = highlighter.getStartOffset();
          boolean willBeRemoved = endOffset == document.getTextLength() && range.getEndOffset() == document.getTextLength()
                                  || range.contains(startOffset)
                                  || range.containsRange(startOffset, endOffset);
          if (willBeRemoved) {
            infosToRemove.recycleHighlighter(highlighter);
            info.highlighter = null;
          }
        }
        return true;
      }
    });

    Collections.sort(highlights, BY_START_OFFSET_NODUPS);
    final Map<TextRange, RangeMarker> ranges2markersCache = new THashMap<TextRange, RangeMarker>(10);
    final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
    final boolean[] changed = {false};
    RangeMarkerTree.sweep(new RangeMarkerTree.Generator<HighlightInfo>(){
      @Override
      public boolean generate(Processor<HighlightInfo> processor) {
        return ContainerUtil.process(highlights, processor);
      }
    }, new MarkupModelEx.SweepProcessor<HighlightInfo>() {
      @Override
      public boolean process(int offset, HighlightInfo info, boolean atStart, Collection<HighlightInfo> overlappingIntervals) {
        if (!atStart) {
          return true;
        }
        if (info.isFileLevelAnnotation && psiFile != null && psiFile.getViewProvider().isPhysical()) {
          addFileLevelHighlight(project, group, info, psiFile);
          changed[0] = true;
          return true;
        }
        if (isWarningCoveredByError(info, overlappingIntervals, severityRegistrar)) {
          return true;
        }
        boolean success = createOrReuseHighlighterFor(info, document, group, psiFile, markup, infosToRemove,
                                          ranges2markersCache, range.getStartOffset(), range.getEndOffset(),
                                          severityRegistrar);
        if (success) {
          changed[0] = true;
        }
        return true;
      }
    });
    for (RangeHighlighter highlighter : infosToRemove.forAllInGarbageBin()) {
      markup.removeHighlighter(highlighter);
      changed[0] = true;
    }

    if (changed[0]) {
      clearWhiteSpaceOptimizationFlag(document);
    }
    assertMarkupConsistent(markup, project);
  }

  private static boolean isWarningCoveredByError(HighlightInfo info,
                                                 Collection<HighlightInfo> overlappingIntervals,
                                                 SeverityRegistrar severityRegistrar) {
    if (!isError(info, severityRegistrar)) {
      for (HighlightInfo overlapping : overlappingIntervals) {
        boolean overlapIsError = isError(overlapping, severityRegistrar);
        if (overlapIsError && DaemonCodeAnalyzerImpl.isCoveredBy(info, overlapping)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isError(HighlightInfo info, SeverityRegistrar severityRegistrar) {
    return severityRegistrar.compare(HighlightSeverity.ERROR, info.getSeverity()) <= 0;
  }

  // return true if changed
  private static boolean createOrReuseHighlighterFor(@NotNull final HighlightInfo info,
                                                     @NotNull final Document document,
                                                     final int group,
                                                     @NotNull final PsiFile psiFile,
                                                     @NotNull MarkupModelEx markup,
                                                     @Nullable HighlightersRecycler infosToRemove,
                                                     @Nullable final Map<TextRange, RangeMarker> ranges2markersCache,
                                                     int rangeStartOffset,
                                                     int rangeEndOffset,
                                                     SeverityRegistrar severityRegistrar) {
    final int infoStartOffset = info.startOffset;
    int infoEndOffset = info.endOffset;
    if (infoStartOffset < rangeStartOffset || infoEndOffset > rangeEndOffset) return false;

    if (infoEndOffset == infoStartOffset && !info.isAfterEndOfLine) {
      infoEndOffset++; //show something in case of empty highlightinfo
    }
    final int docLength = document.getTextLength();
    if (infoEndOffset > docLength) {
      infoEndOffset = docLength;
    }

    info.text = document.getCharsSequence().subSequence(infoStartOffset, infoEndOffset).toString();
    info.group = group;

    int layer = getLayer(info, severityRegistrar);
    RangeHighlighterEx highlighter = infosToRemove == null ? null : (RangeHighlighterEx)infosToRemove.pickupHighlighterFromGarbageBin(info.startOffset, info.endOffset, layer);
    if (highlighter == null) {
      highlighter = createRangeHighlighter(infoStartOffset, infoEndOffset, markup, layer);
    }

    final RangeHighlighterEx finalHighlighter = highlighter;
    final int finalInfoEndOffset = infoEndOffset;
    highlighter.changeAttributesInBatch(new Runnable() {
      public void run() {
        finalHighlighter.setTextAttributes(info.getTextAttributes(psiFile));

        info.highlighter = finalHighlighter;
        finalHighlighter.setAfterEndOfLine(info.isAfterEndOfLine);

        Color color = info.getErrorStripeMarkColor(psiFile);
        finalHighlighter.setErrorStripeMarkColor(color);
        if (info != finalHighlighter.getErrorStripeTooltip()) {
          finalHighlighter.setErrorStripeTooltip(info);
        }
        GutterIconRenderer renderer = info.getGutterIconRenderer();
        finalHighlighter.setGutterIconRenderer(renderer);

        if (ranges2markersCache != null) ranges2markersCache.put(new TextRange(infoStartOffset, finalInfoEndOffset), info.highlighter);
        if (info.quickFixActionRanges != null) {
          List<Pair<HighlightInfo.IntentionActionDescriptor, RangeMarker>> list = new ArrayList<Pair<HighlightInfo.IntentionActionDescriptor, RangeMarker>>(info.quickFixActionRanges.size());
          for (Pair<HighlightInfo.IntentionActionDescriptor, TextRange> pair : info.quickFixActionRanges) {
            TextRange textRange = pair.second;
            RangeMarker marker = getOrCreate(document, ranges2markersCache, textRange);
            list.add(Pair.create(pair.first, marker));
          }
          info.quickFixActionMarkers = new CopyOnWriteArrayList<Pair<HighlightInfo.IntentionActionDescriptor, RangeMarker>>(list);
        }
        info.fixMarker = getOrCreate(document, ranges2markersCache, new TextRange(info.fixStartOffset, info.fixEndOffset));
      }
    });

    assert Comparing.equal(info.getTextAttributes(psiFile), highlighter.getTextAttributes()) : "Info: " +
                                                                                               info.getTextAttributes(psiFile) +
                                                                                               "; highlighter:" +
                                                                                               highlighter.getTextAttributes();
    return true;
  }

  @NotNull
  private static RangeHighlighterEx createRangeHighlighter(int infoStartOffset, int infoEndOffset, MarkupModelEx markup, int layer) {
    return (RangeHighlighterEx)markup.addRangeHighlighter(infoStartOffset, infoEndOffset, layer, null, HighlighterTargetArea.EXACT_RANGE);
  }

  private static int getLayer(HighlightInfo info, SeverityRegistrar severityRegistrar) {
    final HighlightSeverity severity = info.getSeverity();
    int layer;
    if (severity == HighlightSeverity.WARNING) {
      layer = HighlighterLayer.WARNING;
    }
    else if (severityRegistrar.compare(severity, HighlightSeverity.ERROR) >= 0) {
      layer = HighlighterLayer.ERROR;
    }
    else {
      layer = HighlighterLayer.ADDITIONAL_SYNTAX;
    }
    return layer;
  }

  private static RangeMarker getOrCreate(@NotNull Document document, @Nullable Map<TextRange, RangeMarker> ranges2markersCache, @NotNull TextRange textRange) {
    RangeMarker marker = ranges2markersCache == null ? null : ranges2markersCache.get(textRange);
    if (marker == null) {
      marker = document.createRangeMarker(textRange);
      if (ranges2markersCache != null) {
        ranges2markersCache.put(textRange, marker);
      }
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
    HighlightersRecycler toReuse = new HighlightersRecycler();
    if (oldMarkers != null) {
      for (LineMarkerInfo info : oldMarkers) {
        RangeHighlighter highlighter = info.highlighter;
        boolean toRemove = !highlighter.isValid() ||
                           info.updatePass == group &&
                           startOffset <= highlighter.getStartOffset() &&
                           (highlighter.getEndOffset() < endOffset || highlighter.getEndOffset() == document.getTextLength());

        if (toRemove) {
          toReuse.recycleHighlighter(highlighter);
        }
        else {
          array.add(info);
        }
      }
    }

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
      RangeHighlighter marker = toReuse.pickupHighlighterFromGarbageBin(info.startOffset, info.endOffset, HighlighterLayer.ADDITIONAL_SYNTAX);
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

    for (RangeHighlighter highlighter : toReuse.forAllInGarbageBin()) {
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

    final Document document = e.getDocument();
    if (document instanceof DocumentEx && ((DocumentEx)document).isInBulkUpdate()) return;

    final MarkupModel markup = document.getMarkupModel(project);
    assertMarkupConsistent(markup, project);

    int offset = e.getOffset();
    Editor[] editors = EditorFactory.getInstance().getEditors(document, project);
    if (editors.length == 0) return;
    Editor editor = editors[0]; // use any editor - just to fetch SelectInEditorManager
    HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(Math.max(0, offset - 1));
    if (iterator.atEnd()) return;
    final int start = iterator.getStart();
    while (iterator.getEnd() < e.getOffset() + Math.max(e.getOldLength(), e.getNewLength())) {
      iterator.advance();
      if (iterator.atEnd()) return;
    }
    final int end = iterator.getEnd();

    final boolean[] highlightersChanged = {false};
    final List<HighlightInfo> removed = new ArrayList<HighlightInfo>();
    final boolean[] documentChangedInsideHighlighter = {false};
    DaemonCodeAnalyzerImpl.processHighlights(document, project, null, start, end, new Processor<HighlightInfo>() {
      public boolean process(HighlightInfo info) {
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
            documentChangedInsideHighlighter[0] = true;
            toRemove = true;
          }
        }

        if (toRemove) {
          //if (info.type.equals(HighlightInfoType.WRONG_REF)) {
            /*
            markup.removeHighlighter(highlighter);
            */
          //}
          highlightersChanged[0] = true;
          removed.add(info);
        }

        return true;
      }
    });

    for (HighlightInfo info : removed) {
      if (!info.highlighter.isValid() || info.type.equals(HighlightInfoType.WRONG_REF)) {
        markup.removeHighlighter(info.highlighter);
      }
    }
    
    assertMarkupConsistent(markup, project);

    if (highlightersChanged[0] || documentChangedInsideHighlighter[0]) {
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

  private static void assertMarkupConsistent(final MarkupModel markup, Project project) {
    if (!RedBlackTree.VERIFY) {
      return;
    }
    Document document = markup.getDocument();
    DaemonCodeAnalyzerImpl.processHighlights(document, project, null, 0, document.getTextLength(), new Processor<HighlightInfo>() {
      public boolean process(HighlightInfo info) {
        assert ((MarkupModelEx)markup).containsHighlighter(info.highlighter);
        return true;
      }
    });
    RangeHighlighter[] allHighlighters = markup.getAllHighlighters();
    for (RangeHighlighter highlighter : allHighlighters) {
      if (!highlighter.isValid()) continue;
      Object tooltip = highlighter.getErrorStripeTooltip();
      if (!(tooltip instanceof HighlightInfo)) {
        continue;
      }
      final HighlightInfo info = (HighlightInfo)tooltip;
      boolean contains = !DaemonCodeAnalyzerImpl.processHighlights(document, project, null, info.getActualStartOffset(), info.getActualEndOffset(), new Processor<HighlightInfo>() {
        public boolean process(HighlightInfo highlightInfo) {
          return UpdateHighlightersUtil.BY_START_OFFSET_NODUPS.compare(highlightInfo, info) != 0;
        }
      });
      assert contains: info;
    }
  }
}
