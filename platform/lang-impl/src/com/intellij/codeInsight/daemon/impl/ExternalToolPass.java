/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.lang.ExternalLanguageAnnotators;
import com.intellij.lang.Language;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationSession;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.TextRange;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author ven
 */
public class ExternalToolPass extends ProgressableTextEditorHighlightingPass {
  private static final Logger LOG = Logger.getInstance(ExternalToolPass.class);

  private final Document myDocument;
  private final AnnotationHolderImpl myAnnotationHolder;
  private final ExternalToolPassFactory myExternalToolPassFactory;
  private final boolean myMainHighlightingPass;
  private final List<MyData> myAnnotationData = new ArrayList<>();

  private static class MyData {
    private final ExternalAnnotator annotator;
    private final PsiFile psiRoot;
    private final Object collectedInfo;
    private volatile Object annotationResult;

    private MyData(ExternalAnnotator annotator, PsiFile psiRoot, Object collectedInfo) {
      this.annotator = annotator;
      this.psiRoot = psiRoot;
      this.collectedInfo = collectedInfo;
    }
  }

  ExternalToolPass(@NotNull ExternalToolPassFactory factory, @NotNull PsiFile file, @NotNull Editor editor, int startOffset, int endOffset) {
    this(factory, file, editor.getDocument(), editor, startOffset, endOffset, new DefaultHighlightInfoProcessor(), false);
  }

  ExternalToolPass(@NotNull ExternalToolPassFactory factory,
                   @NotNull PsiFile file,
                   @NotNull Document document,
                   @Nullable Editor editor,
                   int startOffset,
                   int endOffset,
                   @NotNull HighlightInfoProcessor processor,
                   boolean mainHighlightingPass) {
    super(file.getProject(), document, "External annotators", file, editor, new TextRange(startOffset, endOffset), false, processor);
    myDocument = document;
    myAnnotationHolder = new AnnotationHolderImpl(new AnnotationSession(file));
    myExternalToolPassFactory = factory;
    myMainHighlightingPass = mainHighlightingPass;
  }

  @Override
  protected void collectInformationWithProgress(@NotNull ProgressIndicator progress) {
    FileViewProvider viewProvider = myFile.getViewProvider();
    HighlightingLevelManager highlightingManager = HighlightingLevelManager.getInstance(myProject);
    Map<PsiFile, List<ExternalAnnotator>> allAnnotators = new HashMap<>();
    int externalAnnotatorsInRoots = 0;
    for (Language language : viewProvider.getLanguages()) {
      PsiFile psiRoot = viewProvider.getPsi(language);
      if (highlightingManager.shouldInspect(psiRoot)) {
        List<ExternalAnnotator> annotators = ExternalLanguageAnnotators.allForFile(language, psiRoot);
        if (!annotators.isEmpty()) {
          externalAnnotatorsInRoots += annotators.size();
          allAnnotators.put(psiRoot, annotators);
        }
      }
    }
    setProgressLimit(externalAnnotatorsInRoots);

    InspectionProfileImpl profile = InspectionProjectProfileManager.getInstance(myProject).getCurrentProfile();
    boolean errorFound = DaemonCodeAnalyzerEx.getInstanceEx(myProject).getFileStatusMap().wasErrorFound(myDocument);
    Editor editor = getEditor();

    for (PsiFile psiRoot : allAnnotators.keySet()) {
      for (ExternalAnnotator annotator : allAnnotators.get(psiRoot)) {
        String shortName = annotator.getPairedBatchInspectionShortName();
        if (shortName != null) {
          HighlightDisplayKey key = HighlightDisplayKey.find(shortName);
          LOG.assertTrue(key != null, "Paired tool '" + shortName + "' not found for external annotator: " + annotator);
          if (!profile.isToolEnabled(key, myFile)) continue;
        }

        Object collectedInfo = editor != null ? annotator.collectInformation(psiRoot, editor, errorFound) : annotator.collectInformation(psiRoot);
        advanceProgress(1);
        if (collectedInfo != null) {
          myAnnotationData.add(new MyData(annotator, psiRoot, collectedInfo));
        }
      }
    }
  }

  @NotNull
  @Override
  public List<HighlightInfo> getInfos() {
    if (myProject.isDisposed()) {
      return Collections.emptyList();
    }
    if (myMainHighlightingPass) {
      doAnnotate();
      applyRelevant();
      return getHighlights();
    }
    return super.getInfos();
  }

  @Override
  protected void applyInformationWithProgress() {
    final long modificationStampBefore = myDocument.getModificationStamp();

    Update update = new Update(myFile) {
      @Override
      public void setRejected() {
        super.setRejected();
        doFinish(getHighlights(), modificationStampBefore);
      }

      @Override
      public void run() {
        if (documentChanged(modificationStampBefore) || myProject.isDisposed()) {
          return;
        }
        doAnnotate();

        ApplicationManagerEx.getApplicationEx().tryRunReadAction(() -> {
          if (documentChanged(modificationStampBefore) || myProject.isDisposed()) {
            return;
          }
          applyRelevant();
          doFinish(getHighlights(), modificationStampBefore);
        });
      }
    };

    myExternalToolPassFactory.scheduleExternalActivity(update);
  }

  private boolean documentChanged(long modificationStampBefore) {
    return myDocument.getModificationStamp() != modificationStampBefore;
  }

  @NotNull
  private List<HighlightInfo> getHighlights() {
    List<HighlightInfo> infos = new ArrayList<>(myAnnotationHolder.size());
    for (Annotation annotation : myAnnotationHolder) {
      infos.add(HighlightInfo.fromAnnotation(annotation));
    }
    return infos;
  }

  @SuppressWarnings("unchecked")
  private void applyRelevant() {
    for (MyData data : myAnnotationData) {
      if (data.annotationResult != null && data.psiRoot != null && data.psiRoot.isValid()) {
        data.annotator.apply(data.psiRoot, data.annotationResult, myAnnotationHolder);
      }
    }
  }

  private void doFinish(@NotNull final List<HighlightInfo> highlights, final long modificationStampBefore) {
    ApplicationManager.getApplication().invokeLater(() -> {
      if (documentChanged(modificationStampBefore) || myProject.isDisposed()) {
        return;
      }
      int start = myRestrictRange.getStartOffset(), end = myRestrictRange.getEndOffset();
      UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, start, end, highlights, getColorsScheme(), getId());
      DaemonCodeAnalyzerEx.getInstanceEx(myProject).getFileStatusMap().markFileUpToDate(myDocument, getId());
    }, ModalityState.stateForComponent(getEditor().getComponent()));
  }

  @SuppressWarnings("unchecked")
  private void doAnnotate() {
    DumbService dumbService = DumbService.getInstance(myProject);
    for (MyData data : myAnnotationData) {
      if (!dumbService.isDumb() || DumbService.isDumbAware(data.annotator)) {
        data.annotationResult = data.annotator.doAnnotate(data.collectedInfo);
      }
    }
  }
}