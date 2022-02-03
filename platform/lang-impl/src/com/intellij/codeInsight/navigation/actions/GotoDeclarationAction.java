// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.navigation.CtrlMouseAction;
import com.intellij.codeInsight.navigation.CtrlMouseData;
import com.intellij.codeInsight.navigation.CtrlMouseInfo;
import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.codeInsight.navigation.action.GotoDeclarationUtil;
import com.intellij.find.actions.ShowUsagesAction;
import com.intellij.ide.util.DefaultPsiElementCellRenderer;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsCollectorImpl;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventPair;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorGutter;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.NlsContexts.PopupTitle;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EDT;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.text.MessageFormat;
import java.util.*;

public class GotoDeclarationAction extends BaseCodeInsightAction implements DumbAware, CtrlMouseAction {

  private static final Logger LOG = Logger.getInstance(GotoDeclarationAction.class);
  private static List<EventPair<?>> ourCurrentEventData = null; // accessed from EDT only

  @SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
    Language language = file != null ? file.getLanguage() : null;
    List<EventPair<?>> currentEventData = ContainerUtil.append(
      ActionsCollectorImpl.actionEventData(e),
      EventFields.CurrentFile.with(language)
    );
    List<EventPair<?>> savedEventData = ourCurrentEventData;
    ourCurrentEventData = currentEventData;
    try {
      super.actionPerformed(e);
    }
    finally {
      ourCurrentEventData = savedEventData;
    }
  }

  static @NotNull List<@NotNull EventPair<?>> getCurrentEventData() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return Objects.requireNonNull(ourCurrentEventData);
  }

  @NotNull
  @Override
  protected CodeInsightActionHandler getHandler() {
    return GotoDeclarationOrUsageHandler2.INSTANCE;
  }

  @Override
  public @Nullable CtrlMouseInfo getCtrlMouseInfo(@NotNull Editor editor, @NotNull PsiFile file, int offset) {
    return GotoDeclarationOrUsageHandler2.getCtrlMouseInfo(editor, file, offset);
  }

  @Override
  public @Nullable CtrlMouseData getCtrlMouseData(@NotNull Editor editor, @NotNull PsiFile file, int offset) {
    return GotoDeclarationOrUsageHandler2.getCtrlMouseData(editor, file, offset);
  }

  @Override
  protected boolean isValidForLookup() {
    return true;
  }

  public static void startFindUsages(@NotNull Editor editor, @NotNull Project project, @NotNull PsiElement element) {
    startFindUsages(editor, project, element, null);
  }

  public static void startFindUsages(@NotNull Editor editor,
                                     @NotNull Project project,
                                     @NotNull PsiElement element,
                                     @Nullable RelativePoint point) {
    if (DumbService.getInstance(project).isDumb()) {
      AnAction action = ActionManager.getInstance().getAction(ShowUsagesAction.ID);
      String name = action.getTemplatePresentation().getText();
      DumbService.getInstance(project).showDumbModeNotification(ActionUtil.getUnavailableMessage(name, false));
    }
    else {
      RelativePoint popupPosition = point != null ? point : JBPopupFactory.getInstance().guessBestPopupLocation(editor);
      ShowUsagesAction.startFindUsages(element, popupPosition, editor);
    }
  }

  @TestOnly
  public static PsiElement findElementToShowUsagesOf(@NotNull Editor editor, int offset) {
    return TargetElementUtil.getInstance().findTargetElement(editor, TargetElementUtil.ELEMENT_NAME_ACCEPTED, offset);
  }

  /**
   * @deprecated use chooseAmbiguousTarget(Project, Editor, int, PsiElementProcessor, String, PsiElement[])
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public static boolean chooseAmbiguousTarget(@NotNull Editor editor,
                                              int offset,
                                              @NotNull PsiElementProcessor<? super PsiElement> processor,
                                              @NotNull @PopupTitle String titlePattern,
                                              PsiElement @Nullable [] elements) {
    Project project = editor.getProject();
    if (project == null) {
      return false;
    }
    return chooseAmbiguousTarget(project, editor, offset, processor, titlePattern, elements);
  }

  // returns true if processor is run or is going to be run after showing popup
  public static boolean chooseAmbiguousTarget(@NotNull final Project project,
                                              @NotNull Editor editor,
                                              int offset,
                                              @NotNull PsiElementProcessor<? super PsiElement> processor,
                                              @NotNull @PopupTitle String titlePattern,
                                              PsiElement @Nullable [] elements) {
    if (TargetElementUtil.inVirtualSpace(editor, offset)) {
      return false;
    }

    final PsiElement[] finalElements = elements;
    Pair<PsiElement[], PsiReference> pair =
      ActionUtil.underModalProgress(project, CodeInsightBundle.message("progress.title.resolving.reference"),
                                    () -> doChooseAmbiguousTarget(editor, offset, finalElements));

    elements = pair.first;
    PsiReference reference = pair.second;

    if (elements.length == 1) {
      PsiElement element = elements[0];
      LOG.assertTrue(element != null);
      processor.execute(element);
      return true;
    }
    if (elements.length > 1) {
      String title;

      if (reference == null) {
        title = titlePattern;
      }
      else {
        final TextRange range = reference.getRangeInElement();
        final String elementText = reference.getElement().getText();
        LOG.assertTrue(range.getStartOffset() >= 0 && range.getEndOffset() <= elementText.length(),
                       Arrays.toString(elements) + ";" + reference);
        final String refText = range.substring(elementText);
        title = MessageFormat.format(titlePattern, refText);
      }

      NavigationUtil.getPsiElementPopup(elements, new DefaultPsiElementCellRenderer(), title, processor).showInBestPositionFor(editor);
      return true;
    }
    return false;
  }

  @NotNull
  private static Pair<PsiElement[], PsiReference> doChooseAmbiguousTarget(@NotNull Editor editor,
                                                                          int offset,
                                                                          PsiElement @Nullable [] elements) {
    final PsiReference reference = TargetElementUtil.findReference(editor, offset);

    if (elements == null || elements.length == 0) {
      elements = reference == null ? PsiElement.EMPTY_ARRAY
                                   : PsiUtilCore.toPsiElementArray(suggestCandidates(reference));
    }
    return new Pair<>(elements, reference);
  }

  @NotNull
  private static Collection<PsiElement> suggestCandidates(@Nullable PsiReference reference) {
    if (reference == null) {
      return Collections.emptyList();
    }
    return TargetElementUtil.getInstance().getTargetCandidates(reference);
  }

  @Nullable
  @TestOnly
  public static PsiElement findTargetElement(Project project, Editor editor, int offset) {
    final PsiElement[] targets = findAllTargetElements(project, editor, offset);
    return targets.length == 1 ? targets[0] : null;
  }

  @TestOnly
  public static @NotNull PsiElement @NotNull [] findAllTargetElements(Project project, Editor editor, int offset) {
    if (TargetElementUtil.inVirtualSpace(editor, offset)) {
      return PsiElement.EMPTY_ARRAY;
    }

    final PsiElement[] targets = findTargetElementsNoVS(project, editor, offset, true);
    return targets != null ? targets : PsiElement.EMPTY_ARRAY;
  }

  private static @NotNull PsiElement @Nullable [] findTargetElementsFromProviders(@NotNull Project project,
                                                                                  @NotNull Editor editor,
                                                                                  int offset) {
    Document document = editor.getDocument();
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (file == null) return null;

    return GotoDeclarationUtil.findTargetElementsFromProviders(editor, offset, file);
  }

  @TestOnly
  public static @NotNull PsiElement @Nullable [] findTargetElementsNoVS(Project project,
                                                                        Editor editor,
                                                                        int offset,
                                                                        boolean lookupAccepted) {
    PsiElement[] fromProviders = findTargetElementsFromProviders(project, editor, offset);
    if (fromProviders == null || fromProviders.length > 0) {
      return fromProviders;
    }

    int flags = TargetElementUtil.getInstance().getAllAccepted() & ~TargetElementUtil.ELEMENT_NAME_ACCEPTED;
    if (!lookupAccepted) {
      flags &= ~TargetElementUtil.LOOKUP_ITEM_ACCEPTED;
    }
    PsiElement element = TargetElementUtil.getInstance().findTargetElement(editor, flags, offset);
    if (element != null) {
      return new PsiElement[]{element};
    }

    // if no references found in injected fragment, try outer document
    if (editor instanceof EditorWindow) {
      EditorWindow window = (EditorWindow)editor;
      return findTargetElementsNoVS(project, window.getDelegate(), window.getDocument().injectedToHost(offset), lookupAccepted);
    }

    return null;
  }

  @Override
  public void update(@NotNull final AnActionEvent event) {
    if (event.getProject() == null ||
        event.getData(EditorGutter.KEY) != null ||
        Boolean.TRUE.equals(event.getData(CommonDataKeys.EDITOR_VIRTUAL_SPACE))) {
      event.getPresentation().setEnabled(false);
      return;
    }

    InputEvent inputEvent = event.getInputEvent();
    Editor editor = event.getData(CommonDataKeys.EDITOR);
    if (editor != null && inputEvent instanceof MouseEvent && event.getPlace().equals(ActionPlaces.MOUSE_SHORTCUT) &&
        EDT.isCurrentThreadEdt() &&
        !EditorUtil.isPointOverText(editor, new RelativePoint((MouseEvent)inputEvent).getPoint(editor.getContentComponent()))) {
      event.getPresentation().setEnabled(false);
      return;
    }

    for (GotoDeclarationHandler handler : GotoDeclarationHandler.EP_NAME.getExtensionList()) {
      String text = handler.getActionText(event.getDataContext());
      if (text != null) {
        Presentation presentation = event.getPresentation();
        presentation.setText(text);
        break;
      }
    }

    super.update(event);
  }
}
