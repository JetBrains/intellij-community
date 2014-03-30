/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class LineMarkersUtil {
  static void setLineMarkersToEditor(@NotNull Project project,
                                     @NotNull Document document,
                                     int startOffset,
                                     int endOffset,
                                     @NotNull Collection<LineMarkerInfo> markers,
                                     int group) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    List<LineMarkerInfo> oldMarkers = DaemonCodeAnalyzerImpl.getLineMarkers(document, project);
    List<LineMarkerInfo> array = new ArrayList<LineMarkerInfo>(oldMarkers == null ? markers.size() : oldMarkers.size());
    MarkupModel markupModel = DocumentMarkupModel.forDocument(document, project, true);
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
      if (oldRenderer == null || renderer == null || !renderer.equals(oldRenderer)) {
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
      highlighter.dispose();
    }

    DaemonCodeAnalyzerImpl.setLineMarkers(document, array, project);
  }
}
