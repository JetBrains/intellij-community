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
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

class LineMarkersUtil {
  private static final Logger LOG = Logger.getInstance(LineMarkersUtil.class);

  static boolean processLineMarkers(@NotNull Project project,
                             @NotNull Document document,
                             @NotNull Segment bounds,
                             int group, // -1 for all
                             @NotNull Processor<LineMarkerInfo> processor) {
    MarkupModelEx markupModel = (MarkupModelEx)DocumentMarkupModel.forDocument(document, project, true);
    return markupModel.processRangeHighlightersOverlappingWith(bounds.getStartOffset(), bounds.getEndOffset(),
                                                        highlighter -> {
                                                          LineMarkerInfo info = getLineMarkerInfo(highlighter);
                                                          if (info == null) return true;
                                                          if (group != -1 && info.updatePass != group) return true;
                                                          return processor.process(info);
                                                        }
    );
  }

  static void setLineMarkersToEditor(@NotNull Project project,
                                     @NotNull Document document,
                                     @NotNull Segment bounds,
                                     @NotNull Collection<LineMarkerInfo> markers,
                                     int group) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    MarkupModelEx markupModel = (MarkupModelEx)DocumentMarkupModel.forDocument(document, project, true);
    HighlightersRecycler toReuse = new HighlightersRecycler();
    processLineMarkers(project, document, bounds, group, info -> {
      toReuse.recycleHighlighter(info.highlighter);
      return true;
    });

    if (LOG.isDebugEnabled()) {
      List<LineMarkerInfo> oldMarkers = DaemonCodeAnalyzerImpl.getLineMarkers(document, project);
      LOG.debug("LineMarkersUtil.setLineMarkersToEditor(markers: "+markers+", group: " + group+
                "); oldMarkers: "+oldMarkers+"; reused: "+toReuse.forAllInGarbageBin().size());
    }

    for (final LineMarkerInfo info : markers) {
      PsiElement element = info.getElement();
      if (element == null) {
        continue;
      }

      TextRange textRange = element.getTextRange();
      if (textRange == null) continue;
      TextRange elementRange = InjectedLanguageManager.getInstance(project).injectedToHost(element, textRange);
      if (!TextRange.containsRange(bounds, elementRange)) {
        continue;
      }
      createOrReuseLineMarker(info, markupModel, toReuse);
    }

    for (RangeHighlighter highlighter : toReuse.forAllInGarbageBin()) {
      highlighter.dispose();
    }
  }

  private static final Key<LineMarkerInfo> LINE_MARKER_INFO = Key.create("LINE_MARKER_INFO");
  private static RangeHighlighter createOrReuseLineMarker(@NotNull LineMarkerInfo info,
                                                          @NotNull MarkupModel markupModel,
                                                          @Nullable HighlightersRecycler toReuse) {
    RangeHighlighter highlighter = toReuse == null ? null : toReuse.pickupHighlighterFromGarbageBin(info.startOffset, info.endOffset, HighlighterLayer.ADDITIONAL_SYNTAX);
    if (highlighter == null) {
      highlighter = markupModel.addRangeHighlighter(info.startOffset, info.endOffset, HighlighterLayer.ADDITIONAL_SYNTAX, null, HighlighterTargetArea.LINES_IN_RANGE);
    }
    highlighter.putUserData(LINE_MARKER_INFO, info);
    LineMarkerInfo.LineMarkerGutterIconRenderer newRenderer = (LineMarkerInfo.LineMarkerGutterIconRenderer)info.createGutterRenderer();
    LineMarkerInfo.LineMarkerGutterIconRenderer oldRenderer = highlighter.getGutterIconRenderer() instanceof LineMarkerInfo.LineMarkerGutterIconRenderer ? (LineMarkerInfo.LineMarkerGutterIconRenderer)highlighter.getGutterIconRenderer() : null;
    boolean rendererChanged = oldRenderer == null || newRenderer == null || !newRenderer.equals(oldRenderer);
    boolean lineSeparatorColorChanged = !Comparing.equal(highlighter.getLineSeparatorColor(), info.separatorColor);
    boolean lineSeparatorPlacementChanged = !Comparing.equal(highlighter.getLineSeparatorPlacement(), info.separatorPlacement);

    if (rendererChanged || lineSeparatorColorChanged || lineSeparatorPlacementChanged) {
      ((MarkupModelEx)markupModel).changeAttributesInBatch((RangeHighlighterEx)highlighter, markerEx -> {
        if (rendererChanged) {
          markerEx.setGutterIconRenderer(newRenderer);
        }
        if (lineSeparatorColorChanged) {
          markerEx.setLineSeparatorColor(info.separatorColor);
        }
        if (lineSeparatorPlacementChanged) {
          markerEx.setLineSeparatorPlacement(info.separatorPlacement);
        }
      });
    }
    info.highlighter = highlighter;
    return highlighter;
  }

  static void addLineMarkerToEditorIncrementally(@NotNull Project project,
                                                 @NotNull Document document,
                                                 @NotNull LineMarkerInfo marker) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    MarkupModelEx markupModel = (MarkupModelEx)DocumentMarkupModel.forDocument(document, project, true);
    boolean allIsClear = markupModel.processRangeHighlightersOverlappingWith(marker.startOffset, marker.endOffset,
                                                                             highlighter -> getLineMarkerInfo(highlighter) == null);
    if (allIsClear) {
      createOrReuseLineMarker(marker, markupModel, null);
    }
  }

  private static LineMarkerInfo getLineMarkerInfo(@NotNull RangeHighlighter highlighter) {
    return highlighter.getUserData(LINE_MARKER_INFO);
  }
}
