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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author ven
 */
public class ExternalToolPass extends ProgressableTextEditorHighlightingPass {
  private static final Logger LOG = Logger.getInstance(ExternalToolPass.class);
  private final AnnotationHolderImpl myAnnotationHolder;

  private final Map<ExternalAnnotator, MyData> myAnnotator2DataMap = new HashMap<>();

  private final ExternalToolPassFactory myExternalToolPassFactory;
  private final boolean myMainHighlightingPass;

  private static class MyData {
    private final PsiFile myPsiRoot;
    private final Object myCollectedInfo;
    private volatile Object myAnnotationResult;

    private MyData(@NotNull PsiFile psiRoot, @NotNull Object collectedInfo) {
      myPsiRoot = psiRoot;
      myCollectedInfo = collectedInfo;
    }
  }

  ExternalToolPass(@NotNull ExternalToolPassFactory externalToolPassFactory,
                   @NotNull PsiFile file,
                   @NotNull Editor editor,
                   int startOffset,
                   int endOffset) {
    super(file.getProject(), editor.getDocument(), "External annotators", file, editor, new TextRange(startOffset, endOffset), false, new DefaultHighlightInfoProcessor());
    myAnnotationHolder = new AnnotationHolderImpl(new AnnotationSession(file));
    myExternalToolPassFactory = externalToolPassFactory;
    myMainHighlightingPass = false;
  }

  ExternalToolPass(@NotNull ExternalToolPassFactory externalToolPassFactory,
                          @NotNull PsiFile file,
                          @NotNull Document document,
                          int startOffset,
                          int endOffset,
                          @NotNull HighlightInfoProcessor highlightInfoProcessor,
                          boolean mainHighlightingPass) {
    super(file.getProject(), document, "External annotators", file, null, new TextRange(startOffset, endOffset), false, highlightInfoProcessor);
    myAnnotationHolder = new AnnotationHolderImpl(new AnnotationSession(file));
    myExternalToolPassFactory = externalToolPassFactory;
    myMainHighlightingPass = mainHighlightingPass;
  }

  @Override
  protected void collectInformationWithProgress(@NotNull ProgressIndicator progress) {
    final FileViewProvider viewProvider = myFile.getViewProvider();
    final Set<Language> relevantLanguages = viewProvider.getLanguages();
    int externalAnnotatorsInRoots = 0;

    for (Language language : relevantLanguages) {
      PsiFile psiRoot = viewProvider.getPsi(language);
      if (!HighlightingLevelManager.getInstance(myProject).shouldInspect(psiRoot)) continue;
      final List<ExternalAnnotator> externalAnnotators = ExternalLanguageAnnotators.allForFile(language, psiRoot);

      externalAnnotatorsInRoots += externalAnnotators.size();
    }
    setProgressLimit(externalAnnotatorsInRoots);

    InspectionProfileImpl profile = InspectionProjectProfileManager.getInstance(myProject).getCurrentProfile();
    for (Language language : relevantLanguages) {
      PsiFile psiRoot = viewProvider.getPsi(language);
      if (!HighlightingLevelManager.getInstance(myProject).shouldInspect(psiRoot)) continue;
      final List<ExternalAnnotator> externalAnnotators = ExternalLanguageAnnotators.allForFile(language, psiRoot);

      if (!externalAnnotators.isEmpty()) {
        DaemonCodeAnalyzerEx daemonCodeAnalyzer = DaemonCodeAnalyzerEx.getInstanceEx(myProject);
        boolean errorFound = daemonCodeAnalyzer.getFileStatusMap().wasErrorFound(myDocument);

        for(ExternalAnnotator externalAnnotator: externalAnnotators) {
          String shortName = externalAnnotator.getPairedBatchInspectionShortName();
          if (shortName != null) {
            HighlightDisplayKey key = HighlightDisplayKey.find(shortName);
            LOG.assertTrue(key != null, "Paired tool '" + shortName + "' not found for external annotator: " + externalAnnotator);
            if (!profile.isToolEnabled(key, myFile)) continue;
          }
          final Object collectedInfo;
          Editor editor = getEditor();
          if (editor != null) {
            collectedInfo = externalAnnotator.collectInformation(psiRoot, editor, errorFound);
          }
          else {
            collectedInfo = externalAnnotator.collectInformation(psiRoot);
          }

          advanceProgress(1);
          if (collectedInfo != null) {
            myAnnotator2DataMap.put(externalAnnotator, new MyData(psiRoot, collectedInfo));
          }
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

  private void applyRelevant() {
    for (ExternalAnnotator annotator : myAnnotator2DataMap.keySet()) {
      final MyData data = myAnnotator2DataMap.get(annotator);
      if (data != null && data.myAnnotationResult != null) {
        PsiFile file = data.myPsiRoot;

        if (!file.isValid()) {
          // the document wasn't changed, so probably PSI was invalidated after reparse; makes sense to find the new root
          VirtualFile vFile = file.getVirtualFile();
          if (vFile != null) {
            file = file.getManager().findFile(vFile);
          }
        }

        if (file != null && file.isValid()) {
          annotator.apply(file, data.myAnnotationResult, myAnnotationHolder);
        }
      }
    }
  }

  private void doFinish(@NotNull final List<HighlightInfo> highlights, final long modificationStampBefore) {
    ApplicationManager.getApplication().invokeLater(() -> {
      if (documentChanged(modificationStampBefore) || myProject.isDisposed()) {
        return;
      }
      UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, myRestrictRange.getStartOffset(), myRestrictRange.getEndOffset(), highlights, getColorsScheme(), getId());
      DaemonCodeAnalyzerEx daemonCodeAnalyzer = DaemonCodeAnalyzerEx.getInstanceEx(myProject);
      daemonCodeAnalyzer.getFileStatusMap().markFileUpToDate(myDocument, getId());
    }, ModalityState.stateForComponent(getEditor().getComponent()));
  }

  private void doAnnotate() {
    for (ExternalAnnotator annotator : DumbService.getInstance(myProject).filterByDumbAwareness(myAnnotator2DataMap.keySet())) {
      final MyData data = myAnnotator2DataMap.get(annotator);
      if (data != null) {
        data.myAnnotationResult = annotator.doAnnotate(data.myCollectedInfo);
      }
    }
  }
}
