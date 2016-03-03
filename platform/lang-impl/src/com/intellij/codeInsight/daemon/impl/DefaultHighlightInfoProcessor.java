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

import com.intellij.codeHighlighting.Pass;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.impl.EditorMarkupModelImpl;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.Alarm;
import com.intellij.util.Processor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DefaultHighlightInfoProcessor extends HighlightInfoProcessor {
  @Override
  public void highlightsInsideVisiblePartAreProduced(@NotNull final HighlightingSession session,
                                                     @NotNull final List<HighlightInfo> infos,
                                                     @NotNull TextRange priorityRange,
                                                     @NotNull TextRange restrictRange,
                                                     final int groupId) {
    final PsiFile psiFile = session.getPsiFile();
    final Project project = psiFile.getProject();
    final Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
    if (document == null) return;
    final long modificationStamp = document.getModificationStamp();
    final TextRange priorityIntersection = priorityRange.intersection(restrictRange);

    final Editor editor = session.getEditor();
    TransactionGuard.submitTransaction(new Runnable() {
      @Override
      public void run() {
        if (project.isDisposed() || modificationStamp != document.getModificationStamp()) return;
        if (priorityIntersection != null) {
          MarkupModel markupModel = DocumentMarkupModel.forDocument(document, project, true);

          EditorColorsScheme scheme = session.getColorsScheme();
          UpdateHighlightersUtil.setHighlightersInRange(project, document, priorityIntersection, scheme, infos,
                                                        (MarkupModelEx)markupModel, groupId);
        }
        if (editor != null && !editor.isDisposed()) {
          // usability: show auto import popup as soon as possible
          new ShowAutoImportPass(project, psiFile, editor).applyInformationToEditor();
          
          DaemonListeners.repaintErrorStripeRenderer(editor, project);
        }
      }
    });
  }

  @Override
  public void highlightsOutsideVisiblePartAreProduced(@NotNull final HighlightingSession session,
                                                      @NotNull final List<HighlightInfo> infos,
                                                      @NotNull final TextRange priorityRange,
                                                      @NotNull final TextRange restrictedRange, final int groupId) {
    final PsiFile psiFile = session.getPsiFile();
    final Project project = psiFile.getProject();
    final Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
    if (document == null) return;
    final long modificationStamp = document.getModificationStamp();
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        if (project.isDisposed() || modificationStamp != document.getModificationStamp()) return;

        EditorColorsScheme scheme = session.getColorsScheme();

        UpdateHighlightersUtil.setHighlightersOutsideRange(project, document, psiFile, infos, scheme,
                                                           restrictedRange.getStartOffset(), restrictedRange.getEndOffset(),
                                                           ProperTextRange.create(priorityRange),
                                                           groupId);
        Editor editor = session.getEditor();
        if (editor != null) {
          DaemonListeners.repaintErrorStripeRenderer(editor, project);
        }
      }
    });

  }

  @Override
  public void allHighlightsForRangeAreProduced(@NotNull HighlightingSession session,
                                               @NotNull TextRange elementRange,
                                               @Nullable List<HighlightInfo> infos) {
    PsiFile psiFile = session.getPsiFile();
    killAbandonedHighlightsUnder(psiFile, elementRange, infos, session);
  }

  private static void killAbandonedHighlightsUnder(@NotNull PsiFile psiFile,
                                                   @NotNull final TextRange range,
                                                   @Nullable final List<HighlightInfo> infos,
                                                   @NotNull final HighlightingSession highlightingSession) {
    final Project project = psiFile.getProject();
    final Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
    if (document == null) return;
    DaemonCodeAnalyzerEx
      .processHighlights(document, project, null, range.getStartOffset(), range.getEndOffset(), new Processor<HighlightInfo>() {
        @Override
        public boolean process(final HighlightInfo existing) {
          if (existing.isBijective() &&
              existing.getGroup() == Pass.UPDATE_ALL &&
              range.equalsToRange(existing.getActualStartOffset(), existing.getActualEndOffset())) {
            if (infos != null) {
              for (HighlightInfo created : infos) {
                if (existing.equalsByActualOffset(created)) return true;
              }
            }
            // seems that highlight info "existing" is going to disappear
            // remove it earlier
            ((HighlightingSessionImpl)highlightingSession).queueDisposeHighlighter(existing.highlighter);
          }
          return true;
        }
      });
  }

  @Override
  public void infoIsAvailable(@NotNull HighlightingSession session,
                              @NotNull HighlightInfo info,
                              @NotNull TextRange priorityRange,
                              @NotNull TextRange restrictedRange,
                              int groupId) {
    HighlightingSessionImpl impl = (HighlightingSessionImpl)session;
    impl.queueHighlightInfo(info, priorityRange, restrictedRange, groupId);
  }

  @Override
  public void progressIsAdvanced(@NotNull HighlightingSession highlightingSession, double progress) {
    PsiFile file = highlightingSession.getPsiFile();
    Editor editor = highlightingSession.getEditor();
    repaintTrafficIcon(file, editor, progress);
  }

  private final Alarm repaintIconAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  private void repaintTrafficIcon(@NotNull final PsiFile file, final Editor editor, double progress) {
    if (ApplicationManager.getApplication().isCommandLine()) return;

    if (repaintIconAlarm.isEmpty() || progress >= 1) {
      repaintIconAlarm.addRequest(new Runnable() {
        @Override
        public void run() {
          Project myProject = file.getProject();
          if (myProject.isDisposed()) return;
          Editor myeditor = editor;
          if (myeditor == null) {
            myeditor = PsiUtilBase.findEditor(file);
          }
          if (myeditor == null || myeditor.isDisposed()) return;
          EditorMarkupModelImpl markup = (EditorMarkupModelImpl)myeditor.getMarkupModel();
          markup.repaintTrafficLightIcon();
          DaemonListeners.repaintErrorStripeRenderer(myeditor, myProject);
        }
      }, 50, null);
    }
  }
}
