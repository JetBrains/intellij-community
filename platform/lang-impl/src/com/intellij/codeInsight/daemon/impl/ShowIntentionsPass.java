// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInsight.intention.impl.*;
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewUnsupportedOperationException;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixUpdater;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.Language;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.Segment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtilBase;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.CommonProcessors;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class ShowIntentionsPass extends TextEditorHighlightingPass implements DumbAware {
  private final Editor myEditor;

  private final PsiFile myFile;
  private final boolean myQueryIntentionActions;
  private final @NotNull ProperTextRange myVisibleRange;
  private volatile CachedIntentions myCachedIntentions;
  private volatile boolean myActionsChanged;

  /**
   *
   * @param queryIntentionActions true if {@link IntentionManager} must be asked for all registered {@link IntentionAction} and {@link IntentionAction#isAvailable(Project, Editor, PsiFile)} must be called on each.
   *                              Usually, this expensive process should be executed only once per highlighting session
   */
  ShowIntentionsPass(@NotNull PsiFile psiFile, @NotNull Editor editor, boolean queryIntentionActions) {
    super(psiFile.getProject(), editor.getDocument(), false);
    myQueryIntentionActions = queryIntentionActions;
    myEditor = editor;
    myFile = psiFile;
    myVisibleRange = HighlightingSessionImpl.getFromCurrentIndicator(psiFile).getVisibleRange();
  }

  public static @NotNull List<HighlightInfo.IntentionActionDescriptor> getAvailableFixes(@NotNull Editor editor,
                                                                                         @NotNull PsiFile file,
                                                                                         int passId,
                                                                                         int offset) {
    Project project = file.getProject();

    List<HighlightInfo.IntentionActionDescriptor> result = new ArrayList<>();
    DaemonCodeAnalyzerImpl.processHighlightsNearOffset(editor.getDocument(), project, HighlightSeverity.INFORMATION, offset, true,
                                                       info-> {
                                                         addAvailableFixesForGroups(info, editor, file, result, passId, offset, true);
                                                         return true;
                                                       });
    return result;
  }

  public static void markActionInvoked(@NotNull Project project,
                                       @NotNull Editor editor,
                                       @NotNull IntentionAction action) {
    int offset = editor instanceof EditorEx ex ? ex.getExpectedCaretOffset() : editor.getCaretModel().getOffset();

    List<HighlightInfo> infos = new ArrayList<>();
    DaemonCodeAnalyzerImpl.processHighlightsNearOffset(editor.getDocument(), project, HighlightSeverity.INFORMATION, offset, true,
                                                       new CommonProcessors.CollectProcessor<>(infos));
    for (HighlightInfo info : infos) {
      info.unregisterQuickFix(action1 -> action1 == action);
    }
  }

  private static void addAvailableFixesForGroups(@NotNull HighlightInfo info,
                                                 @NotNull Editor editor,
                                                 @NotNull PsiFile file,
                                                 @NotNull List<? super HighlightInfo.IntentionActionDescriptor> outList,
                                                 int group,
                                                 int offset,
                                                 boolean checkOffset) {
    if (group != -1 && group != info.getGroup()) return;
    boolean fixRangeIsEmpty = info.getFixTextRange().isEmpty();
    Editor[] injectedEditor = {null};
    PsiFile[] injectedFile = {null};
    ProgressIndicator indicator = ProgressIndicatorProvider.getGlobalProgressIndicator();

    boolean[] hasAvailableAction = {false};
    HighlightInfo.IntentionActionDescriptor[] unavailableAction = {null};
    info.findRegisteredQuickFix((descriptor, range) -> {
      if (!fixRangeIsEmpty && isEmpty(range)) {
        return null;
      }

      if (!DumbService.getInstance(file.getProject()).isUsableInCurrentContext(descriptor.getAction())) {
        return null;
      }

      if (checkOffset && !range.contains(offset) && offset != range.getEndOffset()) {
        return null;
      }
      Editor editorToUse;
      PsiFile fileToUse;
      int offsetToUse;
      if (info.isFromInjection()) {
        if (injectedEditor[0] == null) {
          injectedFile[0] = InjectedLanguageUtilBase.findInjectedPsiNoCommit(file, offset);
          injectedEditor[0] = InjectedLanguageUtil.getInjectedEditorForInjectedFile(editor, injectedFile[0]);
        }
        editorToUse = injectedFile[0] == null ? editor : injectedEditor[0];
        fileToUse = injectedFile[0] == null ? file : injectedFile[0];
        offsetToUse = !(editorToUse instanceof EditorWindow editorWindow)
                      ? offset : editorWindow.logicalPositionToOffset(editorWindow.hostToInjected(editor.offsetToLogicalPosition(offset)));
      }
      else {
        editorToUse = editor;
        fileToUse = file;
        offsetToUse = offset;
      }
      if (indicator != null) {
        indicator.setText(descriptor.getDisplayName());
      }
      if (ShowIntentionActionsHandler.availableFor(fileToUse, editorToUse, offsetToUse, descriptor.getAction())) {
        outList.add(descriptor);
        hasAvailableAction[0] = true;
      }
      else if (unavailableAction[0] == null) {
        unavailableAction[0] = descriptor;
      }
      return null;
    });

    if (!hasAvailableAction[0] && unavailableAction[0] != null) {
      HighlightInfo.IntentionActionDescriptor emptyActionDescriptor = unavailableAction[0].copyWithEmptyAction();
      if (emptyActionDescriptor != null) {
        outList.add(emptyActionDescriptor);
      }
    }
  }

  private static boolean isEmpty(@NotNull Segment segment) {
    return segment.getEndOffset() <= segment.getStartOffset();
  }

  public static final class IntentionsInfo {
    public final List<HighlightInfo.IntentionActionDescriptor> intentionsToShow = ContainerUtil.createLockFreeCopyOnWriteList();
    public final List<HighlightInfo.IntentionActionDescriptor> errorFixesToShow = ContainerUtil.createLockFreeCopyOnWriteList();
    public final List<HighlightInfo.IntentionActionDescriptor> inspectionFixesToShow = ContainerUtil.createLockFreeCopyOnWriteList();
    public final List<AnAction> guttersToShow = ContainerUtil.createLockFreeCopyOnWriteList();
    public final List<HighlightInfo.IntentionActionDescriptor> notificationActionsToShow = ContainerUtil.createLockFreeCopyOnWriteList();
    private int myOffset = -1;
    private HighlightInfoType myHighlightInfoType;
    private @Nullable @NlsContexts.PopupTitle String myTitle;

    public void filterActions(@Nullable PsiFile psiFile) {
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        ThreadingAssertions.assertBackgroundThread();
      }
      IntentionActionFilter[] filters = IntentionActionFilter.EXTENSION_POINT_NAME.getExtensions();
      filter(intentionsToShow, psiFile, filters);
      filter(errorFixesToShow, psiFile, filters);
      filter(inspectionFixesToShow, psiFile, filters);
      filter(notificationActionsToShow, psiFile, filters);
    }

    public @Nullable @NlsContexts.PopupTitle String getTitle() {
      return myTitle;
    }

    public void setTitle(@Nullable @NlsContexts.PopupTitle String title) {
      myTitle = title;
    }

    public void setOffset(int offset) {
      myOffset = offset;
    }

    public int getOffset() {
      return myOffset;
    }

    public HighlightInfoType getHighlightInfoType() {
      return myHighlightInfoType;
    }

    public void setHighlightInfoType(HighlightInfoType highlightInfoType) {
      myHighlightInfoType = highlightInfoType;
    }

    private void filter(@NotNull List<HighlightInfo.IntentionActionDescriptor> descriptors,
                        @Nullable PsiFile psiFile,
                        IntentionActionFilter @NotNull [] filters) {
      for (Iterator<HighlightInfo.IntentionActionDescriptor> it = descriptors.iterator(); it.hasNext(); ) {
        HighlightInfo.IntentionActionDescriptor actionDescriptor = it.next();
        for (IntentionActionFilter filter : filters) {
          if (!filter.accept(actionDescriptor.getAction(), psiFile, myOffset)) {
            it.remove();
            break;
          }
        }
      }
    }

    public boolean isEmpty() {
      return intentionsToShow.isEmpty() &&
             errorFixesToShow.isEmpty() &&
             inspectionFixesToShow.isEmpty() &&
             guttersToShow.isEmpty() &&
             notificationActionsToShow.isEmpty();
    }

    @Override
    public @NonNls String toString() {
      return
        "Errors: " + errorFixesToShow + "; " +
        "Inspection fixes: " + inspectionFixesToShow + "; " +
        "Intentions: " + intentionsToShow + "; " +
        "Gutters: " + guttersToShow + "; "+
        "Notifications: " + notificationActionsToShow;
    }
  }

  @Override
  public void doCollectInformation(@NotNull ProgressIndicator progress) {
    TemplateState state = TemplateManagerImpl.getTemplateState(myEditor);
    if (state != null && !state.isFinished() || myEditor.isDisposed()) {
      return;
    }
    IntentionsInfo intentionsInfo = new IntentionsInfo();
    getActionsToShow(myEditor, myFile, intentionsInfo, -1, myQueryIntentionActions);
    myCachedIntentions = IntentionsUI.getInstance(myProject).getCachedIntentions(myEditor, myFile);
    myActionsChanged = myCachedIntentions.wrapAndUpdateActions(intentionsInfo, false);
    UnresolvedReferenceQuickFixUpdater.getInstance(myProject).startComputingNextQuickFixes(myFile, myEditor, myVisibleRange);
  }

  @Override
  public void doApplyInformationToEditor() {
    ThreadingAssertions.assertEventDispatchThread();

    CachedIntentions cachedIntentions = myCachedIntentions;
    boolean actionsChanged = myActionsChanged;
    TemplateState state = TemplateManagerImpl.getTemplateState(myEditor);
    if ((state == null || state.isFinished()) && cachedIntentions != null && !myEditor.isDisposed()) {
      IntentionsUI.getInstance(myProject).update(cachedIntentions, actionsChanged);
      if (PassExecutorService.LOG.isDebugEnabled()) {
        PassExecutorService.LOG.debug("ShowIntentionsPass id="+getId()+" applied; intentions="+cachedIntentions);
      }
    }
  }

  /**
   * Returns the list of actions to show in the Alt-Enter popup at the caret offset in the given editor.
   */
  public static @NotNull IntentionsInfo getActionsToShow(@NotNull Editor hostEditor, @NotNull PsiFile hostFile) {
    IntentionsInfo result = new IntentionsInfo();
    getActionsToShow(hostEditor, hostFile, result, -1);
    return result;
  }

  /**
   * Collects intention actions from providers intended to be invoked in a background thread.
   */
  public static void getActionsToShow(@NotNull Editor hostEditor, @NotNull PsiFile hostFile, @NotNull IntentionsInfo intentions, int passIdToShowIntentionsFor) {
    getActionsToShow(hostEditor, hostFile, intentions, passIdToShowIntentionsFor, true);
    intentions.filterActions(hostFile);
  }

  private static void getActionsToShow(@NotNull Editor hostEditor,
                                       @NotNull PsiFile hostFile,
                                       @NotNull IntentionsInfo intentions,
                                       int passIdToShowIntentionsFor,
                                       boolean queryIntentionActions) {
    int offset = hostEditor.getCaretModel().getOffset();
    PsiElement psiElement = hostFile.findElementAt(offset);
    if (psiElement != null) PsiUtilCore.ensureValid(psiElement);

    intentions.setOffset(offset);

    List<HighlightInfo.IntentionActionDescriptor> fixes = new ArrayList<>();
    DaemonCodeAnalyzerImpl.HighlightByOffsetProcessor highestPriorityInfoFinder = new DaemonCodeAnalyzerImpl.HighlightByOffsetProcessor(true);
    List<HighlightInfo> infos = new ArrayList<>();
    List<HighlightInfo> additionalInfos = new ArrayList<>();
    Document document = hostEditor.getDocument();
    int line = document.getLineNumber(offset);
    DaemonCodeAnalyzerEx.processHighlights(document, hostFile.getProject(), HighlightSeverity.INFORMATION, 0, document.getTextLength(), info -> {
      if (info.containsOffset(offset, true)) {
        infos.add(info);
      }
      else if (info.getSeverity().equals(HighlightSeverity.ERROR) && info.startOffset <= document.getTextLength() && document.getLineNumber(info.startOffset) == line) {
        additionalInfos.add(info);
      }
      return true;
    });
    for (HighlightInfo info : infos) {
      addAvailableFixesForGroups(info, hostEditor, hostFile, fixes, passIdToShowIntentionsFor, offset, true);
      highestPriorityInfoFinder.process(info);
    }
    if (!ContainerUtil.exists(infos, info -> info.getSeverity().equals(HighlightSeverity.ERROR))) {
      for (HighlightInfo info : additionalInfos) {
        List<HighlightInfo.IntentionActionDescriptor> additionalFixes = new ArrayList<>();
        addAvailableFixesForGroups(info, hostEditor, hostFile, additionalFixes, passIdToShowIntentionsFor, offset, false);
        boolean added = false;
        for (HighlightInfo.IntentionActionDescriptor fix : additionalFixes) {
          if (!ContainerUtil.exists(fixes, descriptor -> descriptor.getAction().getText().equals(fix.getAction().getText()))) {
            fix.setFixRange(info.getFixTextRange());
            fixes.add(fix);
            added = true;
          }
        }
        if (added) {
          highestPriorityInfoFinder.process(info);
          break;
        }
      }
    }

    HighlightInfo infoAtCursor = highestPriorityInfoFinder.getResult();
    intentions.setHighlightInfoType(infoAtCursor != null ? infoAtCursor.type : null);
    if (infoAtCursor == null) {
      intentions.errorFixesToShow.addAll(fixes);
    }
    else {
      fillIntentionsInfoForHighlightInfo(infoAtCursor, intentions, fixes);
    }

    if (queryIntentionActions) {
      getRegisteredIntentionActions(hostEditor, hostFile, intentions, passIdToShowIntentionsFor, offset, psiElement, fixes);
    }
  }

  private static void getRegisteredIntentionActions(@NotNull Editor hostEditor,
                                                    @NotNull PsiFile hostFile,
                                                    @NotNull IntentionsInfo intentions,
                                                    int passIdToShowIntentionsFor,
                                                    int offset,
                                                    @Nullable PsiElement psiElement,
                                                    @NotNull List<? extends HighlightInfo.IntentionActionDescriptor> currentFixes) {
    ProgressIndicator indicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
    PsiFile injectedFile = InjectedLanguageUtilBase.findInjectedPsiNoCommit(hostFile, offset);

    Collection<String> languages = getLanguagesForIntentions(hostFile, psiElement, injectedFile);
    List<IntentionAction> availableIntentions = IntentionManager.getInstance().getAvailableIntentions(languages);

    DumbService dumbService = DumbService.getInstance(hostFile.getProject());
    for (IntentionAction action : availableIntentions) {
      ProgressManager.checkCanceled();
      if (!dumbService.isUsableInCurrentContext(action)) {
        continue;
      }

      if (indicator != null) {
        indicator.setText(action.getFamilyName());
      }
      Pair<PsiFile, Editor> place =
        ShowIntentionActionsHandler.chooseBetweenHostAndInjected(hostFile, hostEditor, offset, injectedFile,
                                                                 (psiFile, editor, o) -> ShowIntentionActionsHandler.availableFor(psiFile, editor, o, action));

      if (place != null) {
        List<IntentionAction> enableDisableIntentionAction = new ArrayList<>();
        enableDisableIntentionAction.add(new EnableDisableIntentionAction(action));
        enableDisableIntentionAction.add(new EditIntentionSettingsAction(action));
        if (IntentionShortcutManager.getInstance().hasShortcut(action)) {
          enableDisableIntentionAction.add(new EditShortcutToIntentionAction(action));
          enableDisableIntentionAction.add(new RemoveIntentionActionShortcut(action));
        }
        else {
          enableDisableIntentionAction.add(new AssignShortcutToIntentionAction(action));
        }
        HighlightInfo.IntentionActionDescriptor descriptor =
          new HighlightInfo.IntentionActionDescriptor(action, enableDisableIntentionAction, null, null, null, null, null);
        if (!currentFixes.contains(descriptor)) {
          intentions.intentionsToShow.add(descriptor);
        }
      }
    }

    if (indicator != null) {
      indicator.setText(CodeInsightBundle.message("progress.text.searching.for.additional.intention.actions.quick.fixes"));
    }
    for (IntentionMenuContributor extension : IntentionMenuContributor.EP_NAME.getExtensionList()) {
      ProgressManager.checkCanceled();
      try {
        if (dumbService.isUsableInCurrentContext(extension)) {
          extension.collectActions(hostEditor, hostFile, intentions, passIdToShowIntentionsFor, offset);
        }
      }
      catch (IntentionPreviewUnsupportedOperationException e) {
        //can collect action on a mock memory editor and produce exceptions - ignore
      }
    }
  }

  private static @NotNull Collection<String> getLanguagesForIntentions(@NotNull PsiFile hostFile,
                                                                       @Nullable PsiElement psiElementAtOffset,
                                                                       @Nullable PsiFile injectedFile) {
    Set<String> languageIds = new HashSet<>();
    for (Language language : hostFile.getViewProvider().getLanguages()) {
      languageIds.add(language.getID());
    }

    if (injectedFile != null) {
      for (Language language : injectedFile.getViewProvider().getLanguages()) {
        languageIds.add(language.getID());
      }
    }

    if (psiElementAtOffset != null) {
      for (PsiElement element = psiElementAtOffset; element != null; element = element.getParent()) {
        languageIds.add(element.getLanguage().getID());
        if (element instanceof PsiFile) break;
      }
    }

    return languageIds;
  }

  public static void fillIntentionsInfoForHighlightInfo(@NotNull HighlightInfo infoAtCursor,
                                                        @NotNull IntentionsInfo intentions,
                                                        @NotNull List<? extends HighlightInfo.IntentionActionDescriptor> fixes) {
    if (intentions.getOffset() < 0) {
      intentions.setOffset(infoAtCursor.getActualStartOffset());
    }
    boolean isError = infoAtCursor.getSeverity() == HighlightSeverity.ERROR;
    for (HighlightInfo.IntentionActionDescriptor fix : fixes) {
      if (fix.isError() && isError) {
        intentions.errorFixesToShow.add(fix);
      }
      else if (fix.isInformation()) {
        intentions.intentionsToShow.add(fix);
      }
      else {
        intentions.inspectionFixesToShow.add(fix);
      }
    }
  }
}

