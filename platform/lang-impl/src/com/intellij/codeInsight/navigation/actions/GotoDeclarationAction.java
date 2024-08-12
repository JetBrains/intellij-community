// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.navigation.CtrlMouseAction;
import com.intellij.codeInsight.navigation.CtrlMouseData;
import com.intellij.codeInsight.navigation.PsiTargetNavigator;
import com.intellij.codeInsight.navigation.action.GotoDeclarationUtil;
import com.intellij.find.actions.ShowUsagesAction;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsCollectorImpl;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventPair;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorGutter;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.NlsContexts.PopupTitle;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
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
  private static final DataKey<GotoDeclarationReporter> GO_TO_DECLARATION_REPORTER_DATA_KEY = DataKey.create("GoToDeclarationReporterKey");

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
    AnActionEvent patchedEvent = getEventWithReporter(e);
    try {
      super.actionPerformed(patchedEvent);
    }
    finally {
      ourCurrentEventData = savedEventData;
    }
  }

  private static @NotNull AnActionEvent getEventWithReporter(@NotNull AnActionEvent e) {
    GotoDeclarationReporter reporter = new GotoDeclarationFUSReporter();
    DataContext context = CustomizedDataContext.withSnapshot(e.getDataContext(), sink -> {
      sink.set(GO_TO_DECLARATION_REPORTER_DATA_KEY, reporter);
    });
    return e.withDataContext(context);
  }

  static @NotNull List<@NotNull EventPair<?>> getCurrentEventData() {
    ThreadingAssertions.assertEventDispatchThread();
    return Objects.requireNonNull(ourCurrentEventData);
  }

  @Override
  protected @NotNull CodeInsightActionHandler getHandler() {
    return new GotoDeclarationOrUsageHandler2(null);
  }

  @Nullable
  GotoDeclarationReporter getReporter(@NotNull DataContext dataContext) {
    return GO_TO_DECLARATION_REPORTER_DATA_KEY.getData(dataContext);
  }

  @Override
  protected @NotNull CodeInsightActionHandler getHandler(@NotNull DataContext dataContext) {
    return new GotoDeclarationOrUsageHandler2(getReporter(dataContext));
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
      DumbService.getInstance(project).showDumbModeNotificationForAction(ActionUtil.getUnavailableMessage(name, false),
                                                                         ShowUsagesAction.ID);
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

  // returns true if processor is run or is going to be run after showing popup
  @SuppressWarnings("UnusedReturnValue")
  public static boolean chooseAmbiguousTarget(@NotNull Editor editor,
                                              int offset,
                                              @NotNull PsiElementProcessor<? super PsiElement> processor,
                                              @NotNull @PopupTitle String titlePattern,
                                              final PsiElement @Nullable [] elements) {
    if (TargetElementUtil.inVirtualSpace(editor, offset)) {
      return false;
    }

    Ref<PsiReference> ref = Ref.create();
    return new PsiTargetNavigator<>(() -> {
      PsiReference reference = TargetElementUtil.findReference(editor, offset);
      ref.set(reference);
      if (elements == null || elements.length == 0) {
        return reference == null ? Collections.emptyList() : suggestCandidates(reference);
      }
      return Arrays.asList(elements);
    }).elementsConsumer((elements1, navigator) -> {
      String title = getTitle(titlePattern, elements1, ref.get());
      navigator.title(title);
    }).navigate(editor, null, element -> processor.execute(element));
  }

  private static @PopupTitle String getTitle(@PopupTitle @NotNull String titlePattern, Collection<PsiElement> elements, PsiReference reference) {
    if (reference == null) {
      return titlePattern;
    }
    final TextRange range = reference.getRangeInElement();
    final String elementText = reference.getElement().getText();
    LOG.assertTrue(range.getStartOffset() >= 0 && range.getEndOffset() <= elementText.length(),
                   elements.toString() + ";" + reference);
    final String refText = range.substring(elementText);
    return MessageFormat.format(titlePattern, refText);
  }

  private static @NotNull Collection<PsiElement> suggestCandidates(@Nullable PsiReference reference) {
    if (reference == null) {
      return Collections.emptyList();
    }
    return TargetElementUtil.getInstance().getTargetCandidates(reference);
  }

  @TestOnly
  public static @Nullable PsiElement findTargetElement(Project project, Editor editor, int offset) {
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
    if (editor instanceof EditorWindow window) {
      return findTargetElementsNoVS(project, window.getDelegate(), window.getDocument().injectedToHost(offset), lookupAccepted);
    }

    return null;
  }

  @Override
  public void update(final @NotNull AnActionEvent event) {
    InputEvent inputEvent = event.getInputEvent();
    boolean isMouseShortcut = inputEvent instanceof MouseEvent && ActionPlaces.MOUSE_SHORTCUT.equals(event.getPlace());

    if (event.getProject() == null ||
        event.getData(EditorGutter.KEY) != null ||
        !isMouseShortcut && Boolean.TRUE.equals(event.getData(CommonDataKeys.EDITOR_VIRTUAL_SPACE))) {
      event.getPresentation().setEnabled(false);
      return;
    }

    Editor editor = event.getData(CommonDataKeys.EDITOR);
    if (editor != null && isMouseShortcut &&
        !Boolean.TRUE.equals(event.getUpdateSession().compute(this, "isPointOverText", ActionUpdateThread.EDT, () ->
          event.getData(PlatformDataKeys.EDITOR_CLICK_OVER_TEXT)))) {
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
