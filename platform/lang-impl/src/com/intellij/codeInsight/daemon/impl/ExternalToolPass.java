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

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightLevelUtil;
import com.intellij.lang.ExternalLanguageAnnotators;
import com.intellij.lang.Language;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationSession;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author ven
 */
public class ExternalToolPass extends TextEditorHighlightingPass {
  private final PsiFile myFile;
  private final int myStartOffset;
  private final int myEndOffset;
  private final AnnotationHolderImpl myAnnotationHolder;

  private volatile DocumentListener myDocumentListener;
  private volatile boolean myDocumentChanged;

  private final Map<ExternalAnnotator, MyData> myAnnotator2DataMap;

  private static class MyData {
    final PsiFile myPsiRoot;
    final Object myCollectedInfo;
    volatile Object myAnnotationResult;

    private MyData(PsiFile psiRoot, Object collectedInfo) {
      myPsiRoot = psiRoot;
      myCollectedInfo = collectedInfo;
    }
  }

  public ExternalToolPass(@NotNull PsiFile file,
                          @NotNull Editor editor,
                          int startOffset,
                          int endOffset) {
    super(file.getProject(), editor.getDocument(), false);
    myFile = file;
    myStartOffset = startOffset;
    myEndOffset = endOffset;
    myAnnotationHolder = new AnnotationHolderImpl(new AnnotationSession(file));

    myAnnotator2DataMap = new HashMap<ExternalAnnotator, MyData>();
  }

  public void doCollectInformation(ProgressIndicator progress) {
    myDocumentChanged = false;

    final FileViewProvider viewProvider = myFile.getViewProvider();
    final Set<Language> relevantLanguages = viewProvider.getLanguages();
    for (Language language : relevantLanguages) {
      PsiFile psiRoot = viewProvider.getPsi(language);
      if (!HighlightLevelUtil.shouldInspect(psiRoot)) continue;
      final List<ExternalAnnotator> externalAnnotators = ExternalLanguageAnnotators.allForFile(language, psiRoot);

      if (!externalAnnotators.isEmpty()) {
        boolean errorFound = ((DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(myProject)).getFileStatusMap().wasErrorFound(myDocument);
        if (errorFound) return;

        for(ExternalAnnotator externalAnnotator: externalAnnotators) {
          externalAnnotator.annotate(psiRoot, myAnnotationHolder);

          final Object collectedInfo = externalAnnotator.collectionInformation(psiRoot);
          if (collectedInfo != null) {
            myAnnotator2DataMap.put(externalAnnotator, new MyData(psiRoot, collectedInfo));
          }
        }
      }
    }
  }

  public void doApplyInformationToEditor() {
    DaemonCodeAnalyzer daemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(myProject);
    ((DaemonCodeAnalyzerImpl)daemonCodeAnalyzer).getFileStatusMap().markFileUpToDate(myDocument, myFile, getId());

    myDocumentListener = new DocumentListener() {
      @Override
      public void beforeDocumentChange(DocumentEvent event) {
      }

      @Override
      public void documentChanged(DocumentEvent event) {
        myDocumentChanged = true;
      }
    };
    myDocument.addDocumentListener(myDocumentListener);

    final Runnable r = new Runnable() {
      @Override
      public void run() {
        if (myDocumentChanged || myProject.isDisposed()) {
          doFinish();
          return;
        }
        doAnnotate();

        ApplicationManager.getApplication().runReadAction(new Runnable() {
          @Override
          public void run() {
            if (myDocumentChanged || myProject.isDisposed()) {
              doFinish();
              return;
            }
            collectHighlighters();

            ApplicationManager.getApplication().invokeLater(new Runnable() {
              @Override
              public void run() {
                if (myDocumentChanged || myProject.isDisposed()) {
                  doFinish();
                  return;
                }

                myDocument.removeDocumentListener(myDocumentListener);
                final List<HighlightInfo> infos = getHighlights();
                UpdateHighlightersUtil
                  .setHighlightersToEditor(myProject, myDocument, myStartOffset, myEndOffset, infos, getColorsScheme(), getId());
              }
            });
          }
        });
      }
    };

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      r.run();
    }
    else {
      ApplicationManager.getApplication().executeOnPooledThread(r);
    }
  }

  private List<HighlightInfo> getHighlights() {
    List<HighlightInfo> infos = new ArrayList<HighlightInfo>();
    for (Annotation annotation : myAnnotationHolder) {
      infos.add(HighlightInfo.fromAnnotation(annotation));
    }
    return infos;
  }

  private void collectHighlighters() {
    for (ExternalAnnotator annotator : myAnnotator2DataMap.keySet()) {
      final MyData data = myAnnotator2DataMap.get(annotator);
      if (data != null) {
        annotator.apply(data.myPsiRoot, data.myAnnotationResult, myAnnotationHolder);
      }
    }
  }

  private void doFinish() {
    myDocument.removeDocumentListener(myDocumentListener);
    final Runnable r = new Runnable() {
      @Override
      public void run() {
        UpdateHighlightersUtil
          .setHighlightersToEditor(myProject, myDocument, myStartOffset, myEndOffset, Collections.<HighlightInfo>emptyList(),
                                   getColorsScheme(), getId());
      }
    };
    if (ApplicationManager.getApplication().isDispatchThread()) {
      r.run();
    }
    else {
      ApplicationManager.getApplication().invokeLater(r);
    }
  }

  private void doAnnotate() {
    for (ExternalAnnotator annotator : myAnnotator2DataMap.keySet()) {
      final MyData data = myAnnotator2DataMap.get(annotator);
      if (data != null) {
        data.myAnnotationResult = annotator.doAnnotate(data.myCollectedInfo);
      }
    }
  }
}
