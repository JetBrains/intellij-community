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

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

class SlowLineMarkersPass extends TextEditorHighlightingPass implements LineMarkersProcessor, DumbAware {
  private final PsiFile myFile;
  @NotNull private final Editor myEditor;
  @NotNull private final TextRange myBounds;

  private volatile Collection<LineMarkerInfo> myMarkers = Collections.emptyList();

  public SlowLineMarkersPass(@NotNull Project project, @NotNull PsiFile file, @NotNull Editor editor, @NotNull TextRange bounds) {
    super(project, editor.getDocument(), false);
    myFile = file;
    myEditor = editor;
    myBounds = bounds;
  }

  @Override
  public void doCollectInformation(@NotNull ProgressIndicator progress) {
    final FileViewProvider viewProvider = myFile.getViewProvider();
    final Set<Language> relevantLanguages = viewProvider.getLanguages();
    List<LineMarkerInfo> markers = new SmartList<LineMarkerInfo>();
    for (Language language : relevantLanguages) {
      PsiElement psiRoot = viewProvider.getPsi(language);
      if (psiRoot == null || !HighlightingLevelManager.getInstance(myProject).shouldHighlight(psiRoot)) continue;
      List<PsiElement> elements = CollectHighlightsUtil.getElementsInRange(psiRoot, myBounds.getStartOffset(), myBounds.getEndOffset());
      final List<LineMarkerProvider> providers = LineMarkersPass.getMarkerProviders(language, myProject);
      addLineMarkers(elements, providers, markers, progress);
      LineMarkersPass.collectLineMarkersForInjected(markers, elements, this, myFile, progress);
    }

    myMarkers = LineMarkersPass.mergeLineMarkers(markers, myEditor);
  }

  @Override
  public void addLineMarkers(@NotNull List<PsiElement> elements,
                             @NotNull List<LineMarkerProvider> providers,
                             @NotNull List<LineMarkerInfo> result,
                             @NotNull ProgressIndicator progress) throws ProcessCanceledException {
    for (LineMarkerProvider provider : providers) {
      provider.collectSlowLineMarkers(elements, result);
    }
  }

  @Override
  public void doApplyInformationToEditor() {
    LineMarkersUtil.setLineMarkersToEditor(myProject, getDocument(), myBounds, myMarkers, getId());

    DaemonCodeAnalyzerEx daemonCodeAnalyzer = DaemonCodeAnalyzerEx.getInstanceEx(myProject);
    daemonCodeAnalyzer.getFileStatusMap().markFileUpToDate(getDocument(), getId());
  }
}

