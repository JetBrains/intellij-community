// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.editor.markup.MarkupEditorFilter;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

final class LineMarkersUtil {
  private static final Logger LOG = Logger.getInstance(LineMarkersUtil.class);
  private static final Object LOCK = ObjectUtils.sentinel(LineMarkersUtil.class.getName());

  static void setLineMarkersToEditor(@NotNull Project project,
                                     @NotNull Document document,
                                     @NotNull TextRange bounds,
                                     @NotNull Collection<? extends LineMarkerInfo<?>> newMarkers,
                                     int group,
                                     @NotNull HighlightingSession highlightingSession) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    ApplicationManager.getApplication().assertReadAccessAllowed();

    MarkupModelEx markupModel = (MarkupModelEx)DocumentMarkupModel.forDocument(document, project, true);
    HighlightersRecycler toReuse = new HighlightersRecycler();
    synchronized (LOCK) {
      try {
        markupModel.processRangeHighlightersOverlappingWith(bounds.getStartOffset(), bounds.getEndOffset(),
          highlighter -> {
            LineMarkerInfo<?> info = getLineMarkerInfo(highlighter);

            if (
              // (recycle) zombie line marker immediately because similar-looking line markers don't merge, unlike regular HighlightInfos
              HighlightingMarkupGrave.isZombieMarkup(highlighter) && highlighter.getGutterIconRenderer() != null
                || group == -1 || info != null && info.updatePass == group) {
              toReuse.recycleHighlighter(highlighter);
            }
            return true;
          }
        );

        for (LineMarkerInfo<?> info : newMarkers) {
          PsiElement element = info.getElement();
          if (element == null) {
            continue;
          }

          TextRange textRange = element.getTextRange();
          if (textRange == null) continue;
          TextRange elementRange = InjectedLanguageManager.getInstance(project).injectedToHost(element, textRange);
          if (bounds.contains(elementRange)) {
            createOrReuseLineMarker(info, markupModel, toReuse);
          }
        }
        if (LOG.isDebugEnabled()) {
          LOG.debug("LineMarkersUtil.setLineMarkersToEditor(" +bounds+
                    "; newMarkers: " + newMarkers + ", group: " + group + "); reused: " + toReuse.forAllInGarbageBin().size());
        }
        UpdateHighlightersUtil.incinerateObsoleteHighlighters(toReuse, highlightingSession);
      }
      finally {
        toReuse.releaseHighlighters();
      }
    }
  }

  private static void createOrReuseLineMarker(@NotNull LineMarkerInfo<?> info,
                                              @NotNull MarkupModelEx markupModel,
                                              @NotNull HighlightersRecycler toReuse) {
    LineMarkerInfo.LineMarkerGutterIconRenderer<?> newRenderer = (LineMarkerInfo.LineMarkerGutterIconRenderer<?>)info.createGutterRenderer();

    RangeHighlighterEx highlighter = toReuse.pickupHighlighterFromGarbageBin(info.startOffset, info.endOffset, HighlighterLayer.ADDITIONAL_SYNTAX);
    if (highlighter == null) {
      highlighter = markupModel.addRangeHighlighterAndChangeAttributes(
        null, info.startOffset, info.endOffset,
        HighlighterLayer.ADDITIONAL_SYNTAX, HighlighterTargetArea.LINES_IN_RANGE, false,
        changeAttributes(info, true, newRenderer, true, true));

      MarkupEditorFilter editorFilter = info.getEditorFilter();
      if (editorFilter != MarkupEditorFilter.EMPTY) {
        highlighter.setEditorFilter(editorFilter);
      }
    }
    else {
      LineMarkerInfo.LineMarkerGutterIconRenderer<?> oldRenderer = highlighter.getGutterIconRenderer() instanceof LineMarkerInfo.LineMarkerGutterIconRenderer<?> line ? line : null;
      boolean rendererChanged = newRenderer == null || !newRenderer.equals(oldRenderer);
      boolean lineSeparatorColorChanged = !Comparing.equal(highlighter.getLineSeparatorColor(), info.separatorColor);
      boolean lineSeparatorPlacementChanged = !Comparing.equal(highlighter.getLineSeparatorPlacement(), info.separatorPlacement);

      if (rendererChanged || lineSeparatorColorChanged || lineSeparatorPlacementChanged) {
        markupModel.changeAttributesInBatch(highlighter, changeAttributes(info, rendererChanged, newRenderer, lineSeparatorColorChanged, lineSeparatorPlacementChanged));
      }
      HighlightingMarkupGrave.unmarkZombieMarkup(highlighter);
    }
    highlighter.putUserData(LINE_MARKER_INFO, info);
    info.highlighter = highlighter;
  }

  @NotNull
  private static Consumer<RangeHighlighterEx> changeAttributes(@NotNull LineMarkerInfo<?> info,
                                                               boolean rendererChanged,
                                                               LineMarkerInfo.LineMarkerGutterIconRenderer<?> newRenderer,
                                                               boolean lineSeparatorColorChanged,
                                                               boolean lineSeparatorPlacementChanged) {
    return markerEx -> {
      if (rendererChanged) {
        markerEx.setGutterIconRenderer(newRenderer);
      }
      if (lineSeparatorColorChanged) {
        markerEx.setLineSeparatorColor(info.separatorColor);
      }
      if (lineSeparatorPlacementChanged) {
        markerEx.setLineSeparatorPlacement(info.separatorPlacement);
      }
    };
  }

  static void addLineMarkerToEditorIncrementally(@NotNull Project project, @NotNull Document document, @NotNull LineMarkerInfo<?> marker) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    ApplicationManager.getApplication().assertReadAccessAllowed();

    MarkupModelEx markupModel = (MarkupModelEx)DocumentMarkupModel.forDocument(document, project, true);
    LineMarkerInfo<?>[] markerInTheWay = {null};
    boolean allIsClear;
    HighlightersRecycler toReuse = new HighlightersRecycler();
    synchronized (LOCK) {
      try {
        allIsClear = markupModel.processRangeHighlightersOverlappingWith(marker.startOffset, marker.endOffset,
          highlighter -> {
            if (HighlightingMarkupGrave.isZombieMarkup(highlighter)) {
              toReuse.recycleHighlighter(highlighter);
              return true;
            }
            LineMarkerInfo<?> info = getLineMarkerInfo(highlighter);
            if (info != null) {
              markerInTheWay[0] = info;
              return false;
            }
            return true;
          });
        if (allIsClear) {
          createOrReuseLineMarker(marker, markupModel, toReuse);
        }
      }
      finally {
        toReuse.releaseHighlighters();
      }
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("addLineMarkerToEditorIncrementally: "+marker+" "+(allIsClear ? "created" : " (was not added because "+markerInTheWay[0] +" was in the way)"));
    }
  }

  static @Nullable LineMarkerInfo<?> getLineMarkerInfo(@NotNull RangeHighlighter highlighter) {
    return highlighter.getUserData(LINE_MARKER_INFO);
  }

  private static final Key<LineMarkerInfo<?>> LINE_MARKER_INFO = Key.create("LINE_MARKER_INFO");
}
