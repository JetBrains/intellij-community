package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.codeInsight.intention.impl.IntentionHintComponent;
import com.intellij.codeInsight.intention.impl.config.IntentionManagerSettings;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ShowIntentionsPass extends TextEditorHighlightingPass {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.ShowIntentionsPass");
  private final Editor myEditor;
  @Nullable private IntentionAction[] myIntentionActions; //null means all actions in IntentionManager

  private final PsiFile myFile;
  private final int myPassIdToShowIntentionsFor;

  ShowIntentionsPass(@NotNull Project project, @NotNull Editor editor, int passId, @Nullable IntentionAction[] intentionActions) {
    super(project, editor.getDocument());
    myPassIdToShowIntentionsFor = passId;
    ApplicationManager.getApplication().assertIsDispatchThread();

    myEditor = editor;
    myIntentionActions = intentionActions;

    myFile = PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument());
    assert myFile != null : FileDocumentManager.getInstance().getFile(myEditor.getDocument());
  }

  public void doCollectInformation(ProgressIndicator progress) {
  }

  public void doApplyInformationToEditor() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    if (!myEditor.getContentComponent().hasFocus()) return;
    TemplateState state = TemplateManagerImpl.getTemplateState(myEditor);
    if (state == null || state.isFinished()) {
      showIntentionActions();
    }
  }

  private void showIntentionActions() {
    DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(myProject);
    if (LookupManager.getInstance(myProject).getActiveLookup() != null) return;

    // do not show intentions if caret is outside visible area
    LogicalPosition caretPos = myEditor.getCaretModel().getLogicalPosition();
    Rectangle visibleArea = myEditor.getScrollingModel().getVisibleArea();
    Point xy = myEditor.logicalPositionToXY(caretPos);
    if (!visibleArea.contains(xy)) return;
    List<HighlightInfo.IntentionActionDescriptor> intentionsToShow = new ArrayList<HighlightInfo.IntentionActionDescriptor>();
    List<HighlightInfo.IntentionActionDescriptor> errorFixesToShow = new ArrayList<HighlightInfo.IntentionActionDescriptor>();
    List<HighlightInfo.IntentionActionDescriptor> inspectionFixesToShow = new ArrayList<HighlightInfo.IntentionActionDescriptor>();
    if (myIntentionActions == null) {
      myIntentionActions = IntentionManager.getInstance().getIntentionActions();
    }
    getActionsToShow(myEditor, myFile, intentionsToShow, errorFixesToShow, inspectionFixesToShow, myIntentionActions, myPassIdToShowIntentionsFor);
    if (myFile instanceof PsiCodeFragment) {
      final PsiCodeFragment.IntentionActionsFilter actionsFilter = ((PsiCodeFragment)myFile).getIntentionActionsFilter();
      if (actionsFilter == null) return;
      if (actionsFilter != PsiCodeFragment.IntentionActionsFilter.EVERYTHING_AVAILABLE) {
        filterIntentionActions(actionsFilter, intentionsToShow);
        filterIntentionActions(actionsFilter, errorFixesToShow);
        filterIntentionActions(actionsFilter, inspectionFixesToShow);
      }
    }

    if (!intentionsToShow.isEmpty() || !errorFixesToShow.isEmpty() || !inspectionFixesToShow.isEmpty()) {
      boolean showBulb = false;
      for (HighlightInfo.IntentionActionDescriptor action : ContainerUtil.concat(errorFixesToShow, inspectionFixesToShow)) {
        if (IntentionManagerSettings.getInstance().isShowLightBulb(action.getAction())) {
          showBulb = true;
          break;
        }
      }
      if (!showBulb) {
        for (HighlightInfo.IntentionActionDescriptor descriptor : intentionsToShow) {
          final IntentionAction action = descriptor.getAction();
          if (IntentionManagerSettings.getInstance().isShowLightBulb(action) && action.isAvailable(myProject, myEditor, myFile)) {
            showBulb = true;
            break;
          }
        }
      }

      if (showBulb) {
        IntentionHintComponent hintComponent = codeAnalyzer.getLastIntentionHint();

        if (hintComponent != null) {
          if (hintComponent.updateActions(intentionsToShow, errorFixesToShow, inspectionFixesToShow)) {
            return;
          }
          codeAnalyzer.setLastIntentionHint(null);
        }
        if (!HintManager.getInstance().hasShownHintsThatWillHideByOtherHint()) {
          hintComponent = IntentionHintComponent.showIntentionHint(myProject, myFile, myEditor, intentionsToShow, errorFixesToShow, inspectionFixesToShow, false);
          codeAnalyzer.setLastIntentionHint(hintComponent);
        }
      }
    }
  }

  private static void filterIntentionActions(final PsiCodeFragment.IntentionActionsFilter actionsFilter, final List<HighlightInfo.IntentionActionDescriptor> intentionActionDescriptors) {
    for (Iterator<HighlightInfo.IntentionActionDescriptor> it = intentionActionDescriptors.iterator(); it.hasNext();) {
        HighlightInfo.IntentionActionDescriptor actionDescriptor = it.next();
        if (!actionsFilter.isAvailable(actionDescriptor.getAction())) it.remove();
      }
  }

  public static void getActionsToShow(final Editor editor, final PsiFile psiFile,
                                final List<HighlightInfo.IntentionActionDescriptor> intentionsToShow,
                                final List<HighlightInfo.IntentionActionDescriptor> errorFixesToShow,
                                final List<HighlightInfo.IntentionActionDescriptor> inspectionFixesToShow,
                                IntentionAction[] allIntentionActions, int passIdToShowIntentionsFor) {
    final PsiElement psiElement = psiFile.findElementAt(editor.getCaretModel().getOffset());
    LOG.assertTrue(psiElement == null || psiElement.isValid(), psiElement);
    final boolean isInProject = psiFile.getManager().isInProject(psiFile);

    int offset = editor.getCaretModel().getOffset();
    Project project = psiFile.getProject();
    for (IntentionAction action : allIntentionActions) {
      if (action instanceof PsiElementBaseIntentionAction) {
        if (!isInProject || !((PsiElementBaseIntentionAction)action).isAvailable(project, editor, psiElement)) continue;
      }
      else if (!action.isAvailable(project, editor, psiFile)) {
        continue;
      }
      List<IntentionAction> enableDisableIntentionAction = new ArrayList<IntentionAction>();
      enableDisableIntentionAction.add(new IntentionHintComponent.EnableDisableIntentionAction(action));
      intentionsToShow.add(new HighlightInfo.IntentionActionDescriptor(action, enableDisableIntentionAction, null));
    }

    List<HighlightInfo.IntentionActionDescriptor> actions = QuickFixAction.getAvailableActions(editor, psiFile, passIdToShowIntentionsFor);
    final DaemonCodeAnalyzer codeAnalyzer = DaemonCodeAnalyzer.getInstance(project);
    HighlightInfo infoAtCursor = ((DaemonCodeAnalyzerImpl)codeAnalyzer).findHighlightByOffset(editor.getDocument(), offset, true);
    if (infoAtCursor == null || infoAtCursor.getSeverity() == HighlightSeverity.ERROR) {
      errorFixesToShow.addAll(actions);
    }
    else {
      inspectionFixesToShow.addAll(actions);
    }
  }
}
