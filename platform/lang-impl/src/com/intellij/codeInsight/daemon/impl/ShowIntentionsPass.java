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
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.AbstractIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInsight.intention.impl.IntentionHintComponent;
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler;
import com.intellij.codeInsight.intention.impl.config.IntentionManagerSettings;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.ide.DataManager;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.IntentionFilterOwner;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PairProcessor;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class ShowIntentionsPass extends TextEditorHighlightingPass {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.ShowIntentionsPass");
  private final Editor myEditor;

  private final PsiFile myFile;
  private final int myPassIdToShowIntentionsFor;
  private final IntentionsInfo myIntentionsInfo = new IntentionsInfo();
  private volatile boolean myShowBulb;
  private volatile boolean myHasToRecreate;

  public static class IntentionsInfo {
    public final List<HighlightInfo.IntentionActionDescriptor> intentionsToShow = new ArrayList<HighlightInfo.IntentionActionDescriptor>();
    public final List<HighlightInfo.IntentionActionDescriptor> errorFixesToShow = new ArrayList<HighlightInfo.IntentionActionDescriptor>();
    public final List<HighlightInfo.IntentionActionDescriptor> inspectionFixesToShow = new ArrayList<HighlightInfo.IntentionActionDescriptor>();
    public final List<HighlightInfo.IntentionActionDescriptor> guttersToShow = new ArrayList<HighlightInfo.IntentionActionDescriptor>();

    public void filterActions(@NotNull IntentionFilterOwner.IntentionActionsFilter actionsFilter) {
      filter(intentionsToShow, actionsFilter);
      filter(errorFixesToShow, actionsFilter);
      filter(inspectionFixesToShow, actionsFilter);
      filter(guttersToShow, actionsFilter);
    }

    private static void filter(List<HighlightInfo.IntentionActionDescriptor> descriptors,
                        IntentionFilterOwner.IntentionActionsFilter actionsFilter) {
      for (Iterator<HighlightInfo.IntentionActionDescriptor> it = descriptors.iterator(); it.hasNext();) {
          HighlightInfo.IntentionActionDescriptor actionDescriptor = it.next();
          if (!actionsFilter.isAvailable(actionDescriptor.getAction())) it.remove();
        }
    }

    public boolean isEmpty() {
      return intentionsToShow.isEmpty() && errorFixesToShow.isEmpty() && inspectionFixesToShow.isEmpty() && guttersToShow.isEmpty();
    }

    @NonNls
    @Override
    public String toString() {
      return "Intentions: " + intentionsToShow + "; Errors: " + errorFixesToShow + "; Inspection fixes: " + inspectionFixesToShow + "; Gutters: " + guttersToShow;
    }
  }

  ShowIntentionsPass(@NotNull Project project, @NotNull Editor editor, int passId) {
    super(project, editor.getDocument(), false);
    myPassIdToShowIntentionsFor = passId;
    ApplicationManager.getApplication().assertIsDispatchThread();

    myEditor = editor;

    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    
    myFile = documentManager.getPsiFile(myEditor.getDocument());
    assert myFile != null : FileDocumentManager.getInstance().getFile(myEditor.getDocument());
  }

  public void doCollectInformation(ProgressIndicator progress) {
    if (!ApplicationManager.getApplication().isUnitTestMode() && !myEditor.getContentComponent().hasFocus()) return;
    TemplateState state = TemplateManagerImpl.getTemplateState(myEditor);
    if (state == null || state.isFinished()) {
      DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(myProject);
      getIntentionActionsToShow();
      updateActions(codeAnalyzer);
    }
  }

  public void doApplyInformationToEditor() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    if (!ApplicationManager.getApplication().isUnitTestMode() && !myEditor.getContentComponent().hasFocus()) return;

    // do not show intentions if caret is outside visible area
    LogicalPosition caretPos = myEditor.getCaretModel().getLogicalPosition();
    Rectangle visibleArea = myEditor.getScrollingModel().getVisibleArea();
    Point xy = myEditor.logicalPositionToXY(caretPos);
    if (!visibleArea.contains(xy)) return;

    TemplateState state = TemplateManagerImpl.getTemplateState(myEditor);
    if (myShowBulb && (state == null || state.isFinished()) && !HintManager.getInstance().hasShownHintsThatWillHideByOtherHint()) {
      DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(myProject);
      codeAnalyzer.setLastIntentionHint(myProject, myFile, myEditor, myIntentionsInfo, myHasToRecreate);
    }
  }

  private void getIntentionActionsToShow() {
    if (LookupManager.getInstance(myProject).getActiveLookup() != null) return;

    getActionsToShow(myEditor, myFile, myIntentionsInfo, myPassIdToShowIntentionsFor);
    if (myFile instanceof IntentionFilterOwner) {
      final IntentionFilterOwner.IntentionActionsFilter actionsFilter = ((IntentionFilterOwner)myFile).getIntentionActionsFilter();
      if (actionsFilter == null) return;
      if (actionsFilter != IntentionFilterOwner.IntentionActionsFilter.EVERYTHING_AVAILABLE) {
        myIntentionsInfo.filterActions(actionsFilter);
      }
    }

    if (myIntentionsInfo.isEmpty()) {
      return;
    }
    myShowBulb = !myIntentionsInfo.guttersToShow.isEmpty();
    if (!myShowBulb) {
      for (HighlightInfo.IntentionActionDescriptor descriptor : ContainerUtil.concat(myIntentionsInfo.errorFixesToShow, myIntentionsInfo.inspectionFixesToShow,myIntentionsInfo.intentionsToShow)) {
        final IntentionAction action = descriptor.getAction();
        if (IntentionManagerSettings.getInstance().isShowLightBulb(action)) {
          myShowBulb = true;
          break;
        }
      }
    }
  }

  private void updateActions(DaemonCodeAnalyzerImpl codeAnalyzer) {
    IntentionHintComponent hintComponent = codeAnalyzer.getLastIntentionHint();
    if (!myShowBulb || hintComponent == null) {
      return;
    }
    Boolean result = hintComponent.updateActions(myIntentionsInfo);
    if (result == null) {
      // reshow all
    }
    else if (result == Boolean.FALSE) {
      myHasToRecreate = true;
    }
    else {
      myShowBulb = false;  // nothing to apply
    }
  }

  public static void getActionsToShow(@NotNull final Editor hostEditor, @NotNull final PsiFile hostFile, @NotNull final IntentionsInfo intentions, int passIdToShowIntentionsFor) {
    final PsiElement psiElement = hostFile.findElementAt(hostEditor.getCaretModel().getOffset());
    LOG.assertTrue(psiElement == null || psiElement.isValid(), psiElement);

    int offset = hostEditor.getCaretModel().getOffset();
    Project project = hostFile.getProject();
    
    for (final IntentionAction action : IntentionManager.getInstance().getAvailableIntentionActions()) {
      Pair<PsiFile, Editor> place =
        ShowIntentionActionsHandler.chooseBetweenHostAndInjected(hostFile, hostEditor, new PairProcessor<PsiFile, Editor>() {
          public boolean process(PsiFile psiFile, Editor editor) {
            return ShowIntentionActionsHandler.availableFor(psiFile, editor, action);
          }
        });

      if (place != null) {
        List<IntentionAction> enableDisableIntentionAction = new ArrayList<IntentionAction>();
        enableDisableIntentionAction.add(new IntentionHintComponent.EnableDisableIntentionAction(action));
        intentions.intentionsToShow.add(new HighlightInfo.IntentionActionDescriptor(action, enableDisableIntentionAction, null));
      }
    }

    List<HighlightInfo.IntentionActionDescriptor> actions = QuickFixAction.getAvailableActions(hostEditor, hostFile, passIdToShowIntentionsFor);
    final DaemonCodeAnalyzer codeAnalyzer = DaemonCodeAnalyzer.getInstance(project);
    final Document document = hostEditor.getDocument();
    HighlightInfo infoAtCursor = ((DaemonCodeAnalyzerImpl)codeAnalyzer).findHighlightByOffset(document, offset, true);
    if (infoAtCursor == null || infoAtCursor.getSeverity() == HighlightSeverity.ERROR) {
      intentions.errorFixesToShow.addAll(actions);
    }
    else {
      intentions.inspectionFixesToShow.addAll(actions);
    }
    final int line = document.getLineNumber(offset);
    DaemonCodeAnalyzerImpl.processHighlights(document, project, null,
                                             document.getLineStartOffset(line),
                                             document.getLineEndOffset(line), new Processor<HighlightInfo>() {
        public boolean process(HighlightInfo info) {
          final GutterIconRenderer renderer = info.getGutterIconRenderer();
          if (renderer == null) {
            return true;
          }
          final AnAction action = renderer.getClickAction();
          if (action == null) {
            return true;
          }
          final String text = renderer.getTooltipText();
          if (text == null) {
            return true;
          }
          final IntentionAction actionAdapter = new AbstractIntentionAction() {
            public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
              final RelativePoint relativePoint = JBPopupFactory.getInstance().guessBestPopupLocation(editor);
              action.actionPerformed(
                new AnActionEvent(relativePoint.toMouseEvent(), DataManager.getInstance().getDataContext(), text, new Presentation(),
                                  ActionManager.getInstance(), 0));
            }

            @NotNull
            public String getText() {
              return text;
            }
          };
          intentions.guttersToShow.add(new HighlightInfo.IntentionActionDescriptor(actionAdapter, Collections.<IntentionAction>emptyList(), text, renderer.getIcon()));
          return true;
        }
      });
  }
}

