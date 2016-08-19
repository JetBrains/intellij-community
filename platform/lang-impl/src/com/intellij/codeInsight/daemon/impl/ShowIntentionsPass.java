/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInsight.intention.impl.IntentionHintComponent;
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler;
import com.intellij.codeInsight.intention.impl.config.IntentionManagerSettings;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.actions.CleanupAllIntention;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.concurrency.JobLauncher;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.Processors;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.*;
import java.util.List;

public class ShowIntentionsPass extends TextEditorHighlightingPass {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.ShowIntentionsPass");
  private final Editor myEditor;

  private final PsiFile myFile;
  private final int myPassIdToShowIntentionsFor;
  private final IntentionsInfo myIntentionsInfo = new IntentionsInfo();
  private volatile boolean myShowBulb;
  private volatile boolean myHasToRecreate;

  @NotNull
  public static List<HighlightInfo.IntentionActionDescriptor> getAvailableActions(@NotNull final Editor editor,
                                                                                  @NotNull final PsiFile file,
                                                                                  final int passId) {
    final int offset = ((EditorEx)editor).getExpectedCaretOffset();
    final Project project = file.getProject();

    List<HighlightInfo> infos = new ArrayList<>();
    DaemonCodeAnalyzerImpl.processHighlightsNearOffset(editor.getDocument(), project, HighlightSeverity.INFORMATION, offset, true,
                                                       new CommonProcessors.CollectProcessor<>(infos));
    List<HighlightInfo.IntentionActionDescriptor> result = new ArrayList<>();
    infos.forEach(info->addAvailableActionsForGroups(info, editor, file, result, passId, offset));
    return result;
  }

  private static void addAvailableActionsForGroups(@NotNull HighlightInfo info,
                                                   @NotNull Editor editor,
                                                   @NotNull PsiFile file,
                                                   @NotNull List<HighlightInfo.IntentionActionDescriptor> outList,
                                                   int group,
                                                   int offset) {
    if (info.quickFixActionMarkers == null) return;
    if (group != -1 && group != info.getGroup()) return;
    boolean fixRangeIsNotEmpty = !info.getFixTextRange().isEmpty();
    Editor injectedEditor = null;
    PsiFile injectedFile = null;
    for (Pair<HighlightInfo.IntentionActionDescriptor, RangeMarker> pair : info.quickFixActionMarkers) {
      HighlightInfo.IntentionActionDescriptor actionInGroup = pair.first;
      RangeMarker range = pair.second;
      if (!range.isValid() || fixRangeIsNotEmpty && isEmpty(range)) continue;

      if (DumbService.isDumb(file.getProject()) && !DumbService.isDumbAware(actionInGroup.getAction())) {
        continue;
      }

      int start = range.getStartOffset();
      int end = range.getEndOffset();
      final Project project = file.getProject();
      if (start > offset || offset > end) {
        continue;
      }
      Editor editorToUse;
      PsiFile fileToUse;
      if (info.isFromInjection()) {
        if (injectedEditor == null) {
          injectedFile = InjectedLanguageUtil.findInjectedPsiNoCommit(file, offset);
          injectedEditor = InjectedLanguageUtil.getInjectedEditorForInjectedFile(editor, injectedFile);
        }
        editorToUse = injectedEditor;
        fileToUse = injectedFile;
      }
      else {
        editorToUse = editor;
        fileToUse = file;
      }
      if (actionInGroup.getAction().isAvailable(project, editorToUse, fileToUse)) {
        outList.add(actionInGroup);
      }
    }
  }

  private static boolean isEmpty(@NotNull Segment segment) {
    return segment.getEndOffset() <= segment.getStartOffset();
  }

  public static class IntentionsInfo {
    public final List<HighlightInfo.IntentionActionDescriptor> intentionsToShow = ContainerUtil.createLockFreeCopyOnWriteList();
    public final List<HighlightInfo.IntentionActionDescriptor> errorFixesToShow = ContainerUtil.createLockFreeCopyOnWriteList();
    public final List<HighlightInfo.IntentionActionDescriptor> inspectionFixesToShow = ContainerUtil.createLockFreeCopyOnWriteList();
    public final List<HighlightInfo.IntentionActionDescriptor> guttersToShow = ContainerUtil.createLockFreeCopyOnWriteList();
    public final List<HighlightInfo.IntentionActionDescriptor> notificationActionsToShow = ContainerUtil.createLockFreeCopyOnWriteList();

    private void filterActions(@NotNull IntentionFilterOwner.IntentionActionsFilter actionsFilter) {
      filter(intentionsToShow, actionsFilter);
      filter(errorFixesToShow, actionsFilter);
      filter(inspectionFixesToShow, actionsFilter);
      filter(guttersToShow, actionsFilter);
    }

    private static void filter(@NotNull List<HighlightInfo.IntentionActionDescriptor> descriptors,
                               @NotNull IntentionFilterOwner.IntentionActionsFilter actionsFilter) {
      for (Iterator<HighlightInfo.IntentionActionDescriptor> it = descriptors.iterator(); it.hasNext();) {
          HighlightInfo.IntentionActionDescriptor actionDescriptor = it.next();
          if (!actionsFilter.isAvailable(actionDescriptor.getAction())) it.remove();
        }
    }

    public boolean isEmpty() {
      return intentionsToShow.isEmpty() && errorFixesToShow.isEmpty() && inspectionFixesToShow.isEmpty() && guttersToShow.isEmpty() && 
             notificationActionsToShow.isEmpty();
    }

    @NonNls
    @Override
    public String toString() {
      return
        "Errors: " + errorFixesToShow + "; " +
        "Inspection fixes: " + inspectionFixesToShow + "; " +
        "Intentions: " + intentionsToShow + "; " +
        "Gutters: " + guttersToShow +
        "Notifications: " + notificationActionsToShow;
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

  @Override
  public void doCollectInformation(@NotNull ProgressIndicator progress) {
    if (!ApplicationManager.getApplication().isUnitTestMode() && !myEditor.getContentComponent().hasFocus()) return;
    TemplateState state = TemplateManagerImpl.getTemplateState(myEditor);
    if (state != null && !state.isFinished()) return;
    DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(myProject);
    getIntentionActionsToShow();
    updateActions(codeAnalyzer);
  }

  @Override
  public void doApplyInformationToEditor() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    if (!ApplicationManager.getApplication().isUnitTestMode() && !myEditor.getContentComponent().hasFocus()) return;

    // do not show intentions if caret is outside visible area
    LogicalPosition caretPos = myEditor.getCaretModel().getLogicalPosition();
    Rectangle visibleArea = myEditor.getScrollingModel().getVisibleArea();
    Point xy = myEditor.logicalPositionToXY(caretPos);
    if (!visibleArea.contains(xy)) return;

    TemplateState state = TemplateManagerImpl.getTemplateState(myEditor);
    if (myShowBulb && (state == null || state.isFinished()) && !HintManager.getInstance().hasShownHintsThatWillHideByOtherHint(false)) {
      DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(myProject);
      codeAnalyzer.setLastIntentionHint(myProject, myFile, myEditor, myIntentionsInfo, myHasToRecreate);
    }
  }

  private void getIntentionActionsToShow() {
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
    myShowBulb = !myIntentionsInfo.guttersToShow.isEmpty() || !myIntentionsInfo.notificationActionsToShow.isEmpty() ||
      ContainerUtil.exists(ContainerUtil.concat(myIntentionsInfo.errorFixesToShow, myIntentionsInfo.inspectionFixesToShow,myIntentionsInfo.intentionsToShow),
                           descriptor -> IntentionManagerSettings.getInstance().isShowLightBulb(descriptor.getAction()));
  }

  private static boolean appendCleanupCode(@NotNull List<HighlightInfo.IntentionActionDescriptor> actionDescriptors, @NotNull PsiFile file) {
    for (HighlightInfo.IntentionActionDescriptor descriptor : actionDescriptors) {
      if (descriptor.canCleanup(file)) {
        final ArrayList<IntentionAction> options = new ArrayList<>();
        options.add(EditCleanupProfileIntentionAction.INSTANCE);
        options.add(CleanupOnScopeIntention.INSTANCE);
        actionDescriptors.add(new HighlightInfo.IntentionActionDescriptor(CleanupAllIntention.INSTANCE, options, "Code Cleanup Options"));
        return true;
      }
    }
    return false;
  }

  private void updateActions(@NotNull DaemonCodeAnalyzerImpl codeAnalyzer) {
    IntentionHintComponent hintComponent = codeAnalyzer.getLastIntentionHint();
    if (!myShowBulb || hintComponent == null || !hintComponent.isForEditor(myEditor)) {
      return;
    }
    IntentionHintComponent.PopupUpdateResult result = hintComponent.updateActions(myIntentionsInfo);
    if (result == IntentionHintComponent.PopupUpdateResult.HIDE_AND_RECREATE) {
      // reshow all
    }
    else if (result == IntentionHintComponent.PopupUpdateResult.CHANGED_INVISIBLE) {
      myHasToRecreate = true;
    }
  }

  public static void getActionsToShow(@NotNull final Editor hostEditor,
                                      @NotNull final PsiFile hostFile,
                                      @NotNull final IntentionsInfo intentions,
                                      int passIdToShowIntentionsFor) {
    final PsiElement psiElement = hostFile.findElementAt(hostEditor.getCaretModel().getOffset());
    LOG.assertTrue(psiElement == null || psiElement.isValid(), psiElement);

    int offset = hostEditor.getCaretModel().getOffset();
    final Project project = hostFile.getProject();

    List<HighlightInfo.IntentionActionDescriptor> fixes = getAvailableActions(hostEditor, hostFile, passIdToShowIntentionsFor);
    final DaemonCodeAnalyzer codeAnalyzer = DaemonCodeAnalyzer.getInstance(project);
    final Document hostDocument = hostEditor.getDocument();
    HighlightInfo infoAtCursor = ((DaemonCodeAnalyzerImpl)codeAnalyzer).findHighlightByOffset(hostDocument, offset, true);
    if (infoAtCursor == null) {
      intentions.errorFixesToShow.addAll(fixes);
    }
    else {
      final boolean isError = infoAtCursor.getSeverity() == HighlightSeverity.ERROR;
      for (HighlightInfo.IntentionActionDescriptor fix : fixes) {
        if (fix.isError() && isError) {
          intentions.errorFixesToShow.add(fix);
        }
        else {
          intentions.inspectionFixesToShow.add(fix);
        }
      }
    }

    for (final IntentionAction action : IntentionManager.getInstance().getAvailableIntentionActions()) {
      Pair<PsiFile, Editor> place =
        ShowIntentionActionsHandler.chooseBetweenHostAndInjected(hostFile, hostEditor,
                                                                 (psiFile, editor) -> ShowIntentionActionsHandler.availableFor(psiFile, editor, action));

      if (place != null) {
        List<IntentionAction> enableDisableIntentionAction = new ArrayList<>();
        enableDisableIntentionAction.add(new IntentionHintComponent.EnableDisableIntentionAction(action));
        enableDisableIntentionAction.add(new IntentionHintComponent.EditIntentionSettingsAction(action));
        HighlightInfo.IntentionActionDescriptor descriptor = new HighlightInfo.IntentionActionDescriptor(action, enableDisableIntentionAction, null);
        if (!fixes.contains(descriptor)) {
          intentions.intentionsToShow.add(descriptor);
        }
      }
    }

    if (HighlightingLevelManager.getInstance(project).shouldInspect(hostFile)) {
      PsiElement intentionElement = psiElement;
      int intentionOffset = offset;
      if (psiElement instanceof PsiWhiteSpace && offset == psiElement.getTextRange().getStartOffset() && offset > 0) {
        final PsiElement prev = hostFile.findElementAt(offset - 1);
        if (prev != null && prev.isValid()) {
          intentionElement = prev;
          intentionOffset = offset - 1;
        }
      }
      if (intentionElement != null && intentionElement.getManager().isInProject(intentionElement)) {
        collectIntentionsFromDoNotShowLeveledInspections(project, hostFile, intentionElement, intentionOffset, intentions);
      }
    }

    final int line = hostDocument.getLineNumber(offset);
    MarkupModelEx model = (MarkupModelEx)DocumentMarkupModel.forDocument(hostDocument, project, true);
    List<RangeHighlighterEx> result = new ArrayList<>();
    Processor<RangeHighlighterEx> processor = Processors.cancelableCollectProcessor(result);
    model.processRangeHighlightersOverlappingWith(hostDocument.getLineStartOffset(line),
                                                  hostDocument.getLineEndOffset(line),
                                                  processor);

    GutterIntentionAction.addActions(hostEditor, intentions, project, result);

    boolean cleanup = appendCleanupCode(intentions.inspectionFixesToShow, hostFile);
    if (!cleanup) {
      appendCleanupCode(intentions.errorFixesToShow, hostFile);
    }
    
    EditorNotificationActions.collectDescriptorsForEditor(hostEditor, intentions.notificationActionsToShow);
  }

  /**
   * Invoked in EDT, each inspection should be fast
   */
  private static void collectIntentionsFromDoNotShowLeveledInspections(final Project project,
                                                                       @NotNull final PsiFile hostFile,
                                                                       PsiElement psiElement,
                                                                       final int offset,
                                                                       @NotNull final IntentionsInfo intentions) {
    if (psiElement != null) {
      if (!psiElement.isPhysical()) {
        VirtualFile virtualFile = hostFile.getVirtualFile();
        String text = hostFile.getText();
        LOG.error("not physical: '" + psiElement.getText() + "' @" + offset + psiElement.getTextRange() +
                  " elem:" + psiElement + " (" + psiElement.getClass().getName() + ")" +
                  " in:" + psiElement.getContainingFile() + " host:" + hostFile + "(" + hostFile.getClass().getName() + ")",
                  new Attachment(virtualFile != null ? virtualFile.getPresentableUrl() : "null", text != null ? text : "null"));
      }
      final List<LocalInspectionToolWrapper> intentionTools = new ArrayList<>();
      final InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
      final InspectionToolWrapper[] tools = profile.getInspectionTools(hostFile);
      for (InspectionToolWrapper toolWrapper : tools) {
        if (toolWrapper instanceof LocalInspectionToolWrapper && !((LocalInspectionToolWrapper)toolWrapper).isUnfair()) {
          final HighlightDisplayKey key = HighlightDisplayKey.find(toolWrapper.getShortName());
          if (profile.isToolEnabled(key, hostFile) &&
              HighlightDisplayLevel.DO_NOT_SHOW.equals(profile.getErrorLevel(key, hostFile))) {
            intentionTools.add((LocalInspectionToolWrapper)toolWrapper);
          }
        }
      }

      if (!intentionTools.isEmpty()) {
        final List<PsiElement> elements = new ArrayList<>();
        PsiElement el = psiElement;
        while (el != null) {
          elements.add(el);
          el = el.getParent();
        }

        final Set<String> dialectIds = InspectionEngine.calcElementDialectIds(elements);
        final LocalInspectionToolSession session = new LocalInspectionToolSession(hostFile, 0, hostFile.getTextLength());
        final Processor<LocalInspectionToolWrapper> processor = toolWrapper -> {
          final LocalInspectionTool localInspectionTool = toolWrapper.getTool();
          final HighlightDisplayKey key = HighlightDisplayKey.find(toolWrapper.getShortName());
          final String displayName = toolWrapper.getDisplayName();
          final ProblemsHolder holder = new ProblemsHolder(InspectionManager.getInstance(project), hostFile, true) {
            @Override
            public void registerProblem(@NotNull ProblemDescriptor problemDescriptor) {
              super.registerProblem(problemDescriptor);
              if (problemDescriptor instanceof ProblemDescriptorBase) {
                final TextRange range = ((ProblemDescriptorBase)problemDescriptor).getTextRange();
                if (range != null && range.contains(offset)) {
                  final QuickFix[] fixes = problemDescriptor.getFixes();
                  if (fixes != null) {
                    for (int k = 0; k < fixes.length; k++) {
                      final IntentionAction intentionAction = QuickFixWrapper.wrap(problemDescriptor, k);
                      final HighlightInfo.IntentionActionDescriptor actionDescriptor =
                        new HighlightInfo.IntentionActionDescriptor(intentionAction, null, displayName, null,
                                                                    key, null, HighlightSeverity.INFORMATION);
                      intentions.intentionsToShow.add(actionDescriptor);
                    }
                  }
                }
              }
            }
          };
          InspectionEngine.createVisitorAndAcceptElements(localInspectionTool, holder, true, session, elements,
                                                          dialectIds, InspectionEngine.getDialectIdsSpecifiedForTool(toolWrapper));
          localInspectionTool.inspectionFinished(session, holder);
          return true;
        };
        JobLauncher.getInstance().invokeConcurrentlyUnderProgress(intentionTools, new DaemonProgressIndicator(), false, processor);
      }
    }
  }
}

