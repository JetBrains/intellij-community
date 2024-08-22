// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.daemon.impl.*;
import com.intellij.codeInsight.intention.AdvertisementAction;
import com.intellij.codeInsight.intention.EmptyIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInsight.intention.impl.config.IntentionManagerSettings;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.icons.AllIcons;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtilBase;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.ExperimentalUI;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Predicate;

public final class CachedIntentions implements IntentionContainer {
  private static final Logger LOG = Logger.getInstance(CachedIntentions.class);

  private final Set<IntentionActionWithTextCaching> myIntentions = new CopyOnWriteArraySet<>();
  private final Set<IntentionActionWithTextCaching> myErrorFixes = new CopyOnWriteArraySet<>();
  private final Set<IntentionActionWithTextCaching> myInspectionFixes = new CopyOnWriteArraySet<>();
  private final Set<IntentionActionWithTextCaching> myGutters = new CopyOnWriteArraySet<>();
  private final Set<IntentionActionWithTextCaching> myNotifications = new CopyOnWriteArraySet<>();
  private int myOffset = -1;
  private HighlightInfoType myHighlightInfoType;

  private final @Nullable Editor myEditor;
  private final @NotNull PsiFile myFile;
  private final @NotNull Project myProject;
  private final @Nullable @NlsContexts.PopupTitle String myTitle;

  private final List<AnAction> myGuttersRaw = ContainerUtil.createLockFreeCopyOnWriteList();

  public CachedIntentions(@NotNull Project project, @NotNull PsiFile file, @Nullable Editor editor) {
    this(project, file, editor, null);
  }

  private CachedIntentions(@NotNull Project project, @NotNull PsiFile file, @Nullable Editor editor, @Nullable @NlsContexts.PopupTitle String title) {
    myProject = project;
    myFile = file;
    myEditor = editor;
    myTitle = title;
  }

  @Override
  public @Nullable @NlsContexts.PopupTitle String getTitle() {
    return myTitle;
  }

  public @NotNull Set<IntentionActionWithTextCaching> getIntentions() {
    return myIntentions;
  }

  @Override
  public @NotNull Set<IntentionActionWithTextCaching> getErrorFixes() {
    return myErrorFixes;
  }

  @Override
  public @NotNull Set<IntentionActionWithTextCaching> getInspectionFixes() {
    return myInspectionFixes;
  }

  public @NotNull Set<IntentionActionWithTextCaching> getGutters() {
    return myGutters;
  }

  public @NotNull Set<IntentionActionWithTextCaching> getNotifications() {
    return myNotifications;
  }

  public @Nullable Editor getEditor() {
    return myEditor;
  }

  public @NotNull PsiFile getFile() {
    return myFile;
  }

  public @NotNull Project getProject() {
    return myProject;
  }

  public int getOffset() {
    return myOffset;
  }

  public HighlightInfoType getHighlightInfoType() {
    return myHighlightInfoType;
  }

  public static @NotNull CachedIntentions create(@NotNull Project project,
                                                 @NotNull PsiFile file,
                                                 @Nullable Editor editor,
                                                 @NotNull ShowIntentionsPass.IntentionsInfo intentions) {
    CachedIntentions res = new CachedIntentions(project, file, editor, intentions.getTitle());
    res.wrapAndUpdateActions(intentions, false);
    return res;
  }

  public static @NotNull CachedIntentions createAndUpdateActions(@NotNull Project project,
                                                                 @NotNull PsiFile file,
                                                                 @Nullable Editor editor,
                                                                 @NotNull ShowIntentionsPass.IntentionsInfo intentions) {
    CachedIntentions res = new CachedIntentions(project, file, editor, intentions.getTitle());
    res.wrapAndUpdateActions(intentions, true);
    return res;
  }

  public boolean wrapAndUpdateActions(@NotNull ShowIntentionsPass.IntentionsInfo newInfo, boolean callUpdate) {
    myOffset = newInfo.getOffset();
    myHighlightInfoType = newInfo.getHighlightInfoType();
    boolean changed = wrapActionsTo(newInfo.errorFixesToShow, myErrorFixes, callUpdate);
    changed |= wrapActionsTo(newInfo.inspectionFixesToShow, myInspectionFixes, callUpdate);
    changed |= wrapActionsTo(newInfo.intentionsToShow, myIntentions, callUpdate);
    changed |= updateGuttersRaw(newInfo);
    changed |= wrapActionsTo(newInfo.notificationActionsToShow, myNotifications, callUpdate);
    return changed;
  }

  private boolean updateGuttersRaw(@NotNull ShowIntentionsPass.IntentionsInfo newInfo) {
    if (newInfo.guttersToShow.isEmpty()) return false;
    myGuttersRaw.addAll(newInfo.guttersToShow);
    return true;
  }

  public boolean addActions(@NotNull ShowIntentionsPass.IntentionsInfo info) {
    boolean changed = addActionsTo(info.errorFixesToShow, myErrorFixes);
    changed |= addActionsTo(info.inspectionFixesToShow, myInspectionFixes);
    changed |= addActionsTo(info.intentionsToShow, myIntentions);
    changed |= updateGuttersRaw(info);
    changed |= addActionsTo(info.notificationActionsToShow, myNotifications);
    return changed;
  }

  public void wrapAndUpdateGutters() {
    LOG.assertTrue(myEditor != null);
    if (myGuttersRaw.isEmpty()) return;
    myGutters.clear();

    Predicate<IntentionAction> filter = action -> ContainerUtil.and(
      IntentionActionFilter.EXTENSION_POINT_NAME.getExtensionList(), f -> f.accept(action, myFile, myOffset));

    DefaultActionGroup group = new DefaultActionGroup(new ArrayList<>(new LinkedHashSet<>(myGuttersRaw)));
    PresentationFactory presentationFactory = new PresentationFactory();
    List<AnAction> actions = Utils.expandActionGroup(
      group, presentationFactory,
      EditorUtil.getEditorDataContext(myEditor), ActionPlaces.INTENTION_MENU, ActionUiKind.POPUP);
    List<HighlightInfo.IntentionActionDescriptor> descriptors = new ArrayList<>();
    int order = 0;
    boolean hasSeparatorAbove = false;
    for (AnAction action : actions) {
      Presentation presentation = presentationFactory.getPresentation(action);
      if (action instanceof SeparatorAction) {
        hasSeparatorAbove = true;
        continue;
      }
      else if (StringUtil.isEmpty(presentation.getText())) {
        continue;
      }
      GutterIntentionAction intentionAction = new GutterIntentionAction(action, order++, hasSeparatorAbove);
      intentionAction.updateFromPresentation(presentation);
      if (!filter.test(intentionAction)) continue;
      HighlightInfo.IntentionActionDescriptor descriptor = new HighlightInfo.IntentionActionDescriptor(
        intentionAction, Collections.emptyList(), intentionAction.getText(), intentionAction.getIcon(0), null, null, null);
      descriptors.add(descriptor);
      hasSeparatorAbove = false;
    }
    wrapActionsTo(descriptors, myGutters, false);
  }

  private boolean addActionsTo(@NotNull List<? extends HighlightInfo.IntentionActionDescriptor> newDescriptors,
                               @NotNull Set<? super IntentionActionWithTextCaching> cachedActions) {
    boolean changed = false;
    for (HighlightInfo.IntentionActionDescriptor descriptor : newDescriptors) {
      changed |= cachedActions.add(wrapAction(descriptor, myFile, myFile, myEditor));
    }
    return changed;
  }

  private boolean wrapActionsTo(@NotNull List<? extends HighlightInfo.IntentionActionDescriptor> newDescriptors,
                                @NotNull Set<? super IntentionActionWithTextCaching> cachedActions,
                                boolean shouldCallIsAvailable) {
    if (cachedActions.isEmpty() && newDescriptors.isEmpty()) return false;
    boolean changed = false;
    if (myEditor == null) {
      LOG.assertTrue(!shouldCallIsAvailable);
      for (HighlightInfo.IntentionActionDescriptor descriptor : newDescriptors) {
        changed |= cachedActions.add(wrapAction(descriptor, myFile, myFile, null));
      }
      return changed;
    }
    int caretOffset = myOffset >= 0 ? myOffset : myEditor.getCaretModel().getOffset();
    int fileOffset = caretOffset > 0 && caretOffset == myFile.getTextLength() ? caretOffset - 1 : caretOffset;
    PsiElement element;
    PsiElement hostElement;
    if (myFile instanceof PsiCompiledElement || myFile.getTextLength() == 0) {
      hostElement = element = myFile;
    }
    else if (PsiDocumentManager.getInstance(myProject).isUncommited(myEditor.getDocument())) {
      //???
      FileViewProvider viewProvider = myFile.getViewProvider();
      hostElement = element = viewProvider.findElementAt(fileOffset, viewProvider.getBaseLanguage());
    }
    else {
      hostElement = myFile.getViewProvider().findElementAt(fileOffset, myFile.getLanguage());
      element = InjectedLanguageUtilBase.findElementAtNoCommit(myFile, fileOffset);
    }
    PsiFile injectedFile;
    Editor injectedEditor;
    int injectedOffset;
    if (element == null || element == hostElement) {
      injectedFile = myFile;
      injectedEditor = myEditor;
      injectedOffset = caretOffset;
    }
    else {
      injectedFile = element.getContainingFile();
      injectedEditor = InjectedLanguageUtil.getInjectedEditorForInjectedFile(myEditor, injectedFile);
      if (injectedEditor instanceof EditorWindow editorWindow) {
        injectedOffset = editorWindow.logicalPositionToOffset(editorWindow.hostToInjected(myEditor.offsetToLogicalPosition(fileOffset)));
      }
      else {
        injectedOffset = fileOffset;
      }
    }

    Set<IntentionActionWithTextCaching> wrappedNew = new LinkedHashSet<>(newDescriptors.size());
    for (HighlightInfo.IntentionActionDescriptor descriptor : newDescriptors) {
      IntentionAction action = descriptor.getAction();
      if (element != null &&
          element != hostElement &&
          (!shouldCallIsAvailable || ShowIntentionActionsHandler.availableFor(injectedFile, injectedEditor, injectedOffset, action))) {
        IntentionActionWithTextCaching cachedAction = wrapAction(descriptor, element, injectedFile, injectedEditor);
        wrappedNew.add(cachedAction);
      }
      else if (hostElement != null && (!shouldCallIsAvailable || ShowIntentionActionsHandler.availableFor(myFile, myEditor, fileOffset, action))) {
        IntentionActionWithTextCaching cachedAction = wrapAction(descriptor, hostElement, myFile, myEditor);
        wrappedNew.add(cachedAction);
      }
    }

    if (cachedActions.equals(wrappedNew)) {
      return false;
    }
    cachedActions.addAll(wrappedNew);
    return true;
  }

  @NotNull
  IntentionActionWithTextCaching wrapAction(@NotNull HighlightInfo.IntentionActionDescriptor descriptor,
                                            @NotNull PsiElement element,
                                            @NotNull PsiFile containingFile,
                                            @Nullable Editor containingEditor) {
    IntentionActionWithTextCaching cachedAction =
      new IntentionActionWithTextCaching(
        descriptor.getAction(), descriptor.getDisplayName(), descriptor.getIcon(), descriptor.getToolId(),
        descriptor.getFixRange(), (cached, action) -> {
          if (QuickFixWrapper.unwrap(action) != null) {
            // remove only inspection fixes after invocation,
            // since intention actions might be still available
            removeActionFromCached(cached);
            markInvoked(action);
          }
        });
    for (IntentionAction option : descriptor.getOptions(element, containingEditor)) {
      Editor editor = ObjectUtils.chooseNotNull(myEditor, containingEditor);
      if (editor == null) continue;
      var problemOffset = myOffset >= 0 ? myOffset : editor.getCaretModel().getOffset();
      Pair<PsiFile, Editor> availableIn = ShowIntentionActionsHandler
        .chooseBetweenHostAndInjected(myFile, editor, problemOffset, containingFile,
                                      (f, e, o) -> ShowIntentionActionsHandler.availableFor(f, e, o, option));
      if (availableIn == null) continue;
      IntentionActionWithTextCaching textCaching = new IntentionActionWithTextCaching(option);
      boolean isErrorFix = myErrorFixes.contains(textCaching);
      if (isErrorFix) {
        cachedAction.addErrorFix(option);
      }
      boolean isInspectionFix = myInspectionFixes.contains(textCaching);
      if (isInspectionFix) {
        cachedAction.addInspectionFix(option);
      }
      else {
        cachedAction.addIntention(option);
      }
    }
    return cachedAction;
  }

  private void markInvoked(@NotNull IntentionAction action) {
    if (myEditor != null) {
      ShowIntentionsPass.markActionInvoked(myFile.getProject(), myEditor, action);
    }
  }

  private void removeActionFromCached(@NotNull IntentionActionWithTextCaching action) {
    // remove from the action from the list after invocation to make it appear unavailable sooner.
    // (the highlighting will process the whole file and remove the no more available action from the list automatically - but it may be too long)
    myErrorFixes.remove(action);
    myGutters.remove(action);
    myInspectionFixes.remove(action);
    myIntentions.remove(action);
    myNotifications.remove(action);
  }

  @Override
  public @NotNull List<IntentionActionWithTextCaching> getAllActions() {
    List<IntentionActionWithTextCaching> result = new ArrayList<>(myErrorFixes);
    result.addAll(myInspectionFixes);
    for (IntentionActionWithTextCaching intention : myIntentions) {
      if (!myErrorFixes.contains(intention) && !myInspectionFixes.contains(intention)) {
        result.add(intention);
      }
    }
    result.addAll(myGutters);
    result.addAll(myNotifications);
    result = DumbService.getInstance(myProject).filterByDumbAwareness(result);

    Language language = PsiUtilCore.getLanguageAtOffset(getFile(), getOffset());
    IntentionsOrderProvider intentionsOrder = IntentionsOrderProvider.EXTENSION.forLanguage(language);
    return intentionsOrder.getSortedIntentions(this, result);
  }

  @Override
  public @NotNull IntentionGroup getGroup(@NotNull IntentionActionWithTextCaching action) {
    if (myErrorFixes.contains(action)) {
      TextRange problemRange = action.getFixRange();
      return problemRange == null || problemRange.contains(getOffset()) ? IntentionGroup.ERROR : IntentionGroup.REMOTE_ERROR;
    }
    if (myInspectionFixes.contains(action)) {
      return IntentionGroup.INSPECTION;
    }
    if (myNotifications.contains(action)) {
      return IntentionGroup.NOTIFICATION;
    }
    if (myGutters.contains(action)) {
      return IntentionGroup.GUTTER;
    }
    if (action.getAction() instanceof EmptyIntentionAction) {
      return IntentionGroup.EMPTY_ACTION;
    }
    if (IntentionActionDelegate.unwrap(action.getAction()) instanceof AdvertisementAction) {
      return IntentionGroup.ADVERTISEMENT;
    }

    return IntentionGroup.OTHER;
  }

  /** Determine the icon that is shown in the action menu. */
  @Override
  public @Nullable Icon getIcon(@NotNull IntentionActionWithTextCaching value) {
    if (value.getIcon() != null) {
      return value.getIcon();
    }

    IntentionAction action = IntentionActionDelegate.unwrap(value.getAction());
    Object iconable = action;
    //custom icon
    LocalQuickFix fix = QuickFixWrapper.unwrap(action);
    if (fix != null) {
      iconable = fix;
    }

    if (iconable instanceof Iconable) {
      Icon icon = ((Iconable)iconable).getIcon(0);
      if (icon != null) {
        return icon;
      }
    }

    return ReadAction.compute(() -> {
      if (IntentionManagerSettings.getInstance().isShowLightBulb(action)) {
        return myErrorFixes.contains(value) ? AllIcons.Actions.QuickfixBulb :
               myInspectionFixes.contains(value) ? AllIcons.Actions.IntentionBulb :
               ExperimentalUI.isNewUI() ? null :
               AllIcons.Actions.RealIntentionBulb;
      }
      else {
        return myErrorFixes.contains(value) ? AllIcons.Actions.QuickfixOffBulb :
               ExperimentalUI.isNewUI() ? null :
               IconLoader.getDisabledIcon(AllIcons.Actions.RealIntentionBulb);
      }
    });
  }

  public boolean showBulb() {
    return ContainerUtil.exists(getAllActions(), info -> IntentionManagerSettings.getInstance().isShowLightBulb(info.getAction()));
  }

  @Override
  public String toString() {
    return "CachedIntentions{" +
           "myIntentions=" + myIntentions +
           ", myErrorFixes=" + myErrorFixes +
           ", myInspectionFixes=" + myInspectionFixes +
           ", myGutters=" + myGutters +
           ", myNotifications=" + myNotifications +
           '}';
  }
}
