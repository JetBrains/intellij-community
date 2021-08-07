// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.daemon.impl.GutterIntentionAction;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.IntentionActionFilter;
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass;
import com.intellij.codeInsight.intention.EmptyIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInsight.intention.impl.config.IntentionManagerSettings;
import com.intellij.codeInspection.SuppressIntentionActionFromFix;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtilBase;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashingStrategy;
import com.intellij.util.ui.EmptyIcon;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.function.Predicate;

public final class CachedIntentions {
  private static final Logger LOG = Logger.getInstance(CachedIntentions.class);

  private final Set<IntentionActionWithTextCaching> myIntentions = ConcurrentCollectionFactory.createConcurrentSet(ACTION_TEXT_AND_CLASS_EQUALS);
  private final Set<IntentionActionWithTextCaching> myErrorFixes = ConcurrentCollectionFactory.createConcurrentSet(ACTION_TEXT_AND_CLASS_EQUALS);
  private final Set<IntentionActionWithTextCaching> myInspectionFixes = ConcurrentCollectionFactory.createConcurrentSet(ACTION_TEXT_AND_CLASS_EQUALS);
  private final Set<IntentionActionWithTextCaching> myGutters = ConcurrentCollectionFactory.createConcurrentSet(ACTION_TEXT_AND_CLASS_EQUALS);
  private final Set<IntentionActionWithTextCaching> myNotifications = ConcurrentCollectionFactory.createConcurrentSet(ACTION_TEXT_AND_CLASS_EQUALS);
  private int myOffset;
  @Nullable
  private final Editor myEditor;
  @NotNull
  private final PsiFile myFile;
  @NotNull
  private final Project myProject;

  private final List<AnAction> myGuttersRaw = ContainerUtil.createLockFreeCopyOnWriteList();

  public CachedIntentions(@NotNull Project project, @NotNull PsiFile file, @Nullable Editor editor) {
    myProject = project;
    myFile = file;
    myEditor = editor;
  }

  @NotNull
  public Set<IntentionActionWithTextCaching> getIntentions() {
    return myIntentions;
  }

  @NotNull
  public Set<IntentionActionWithTextCaching> getErrorFixes() {
    return myErrorFixes;
  }

  @NotNull
  public Set<IntentionActionWithTextCaching> getInspectionFixes() {
    return myInspectionFixes;
  }

  @NotNull
  public Set<IntentionActionWithTextCaching> getGutters() {
    return myGutters;
  }

  @NotNull
  public Set<IntentionActionWithTextCaching> getNotifications() {
    return myNotifications;
  }

  @Nullable
  public Editor getEditor() {
    return myEditor;
  }

  @NotNull
  public PsiFile getFile() {
    return myFile;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  public int getOffset() {
    return myOffset;
  }

  @NotNull
  public static CachedIntentions create(@NotNull Project project, @NotNull PsiFile file, @Nullable Editor editor, @NotNull ShowIntentionsPass.IntentionsInfo intentions) {
    CachedIntentions res = new CachedIntentions(project, file, editor);
    res.wrapAndUpdateActions(intentions, false);
    return res;
  }

  @NotNull
  public static CachedIntentions createAndUpdateActions(@NotNull Project project, @NotNull PsiFile file, @Nullable Editor editor, @NotNull ShowIntentionsPass.IntentionsInfo intentions) {
    CachedIntentions res = new CachedIntentions(project, file, editor);
    res.wrapAndUpdateActions(intentions, true);
    return res;
  }

  private static final IntentionActionWithTextCachingHashingStrategy ACTION_TEXT_AND_CLASS_EQUALS =
    new IntentionActionWithTextCachingHashingStrategy();

  public boolean wrapAndUpdateActions(@NotNull ShowIntentionsPass.IntentionsInfo newInfo, boolean callUpdate) {
    myOffset = newInfo.getOffset();
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
      IntentionActionFilter.EXTENSION_POINT_NAME.getExtensionList(), f -> f.accept(action, myFile));

    DataContext dataContext = Utils.wrapDataContext(EditorUtil.getEditorDataContext(myEditor));
    PresentationFactory presentationFactory = new PresentationFactory();
    List<AnAction> actions = Utils.expandActionGroup(
      false, new DefaultActionGroup(myGuttersRaw), presentationFactory,
      dataContext, ActionPlaces.INTENTION_MENU);
    List<HighlightInfo.IntentionActionDescriptor> descriptors = new ArrayList<>();
    int order = 0;
    for (AnAction action : actions) {
      Presentation presentation = presentationFactory.getPresentation(action);
      Icon icon = ObjectUtils.notNull(presentation.getIcon(), EmptyIcon.ICON_16);
      String text = presentation.getText();
      if (StringUtil.isEmpty(text)) continue;
      IntentionAction intentionAction = new GutterIntentionAction(action, order++, icon, text);
      if (!filter.test(intentionAction)) continue;
      HighlightInfo.IntentionActionDescriptor descriptor = new HighlightInfo.IntentionActionDescriptor(
        intentionAction, Collections.emptyList(), text, icon, null, null, null);
      descriptors.add(descriptor);
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
                                @NotNull Set<IntentionActionWithTextCaching> cachedActions,
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
    final int caretOffset = myEditor.getCaretModel().getOffset();
    final int fileOffset = caretOffset > 0 && caretOffset == myFile.getTextLength() ? caretOffset - 1 : caretOffset;
    PsiElement element;
    final PsiElement hostElement;
    if (myFile instanceof PsiCompiledElement) {
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
    if (element == null || element == hostElement) {
      injectedFile = myFile;
      injectedEditor = myEditor;
    }
    else {
      injectedFile = element.getContainingFile();
      injectedEditor = InjectedLanguageUtil.getInjectedEditorForInjectedFile(myEditor, injectedFile);
    }

    if (shouldCallIsAvailable) {
      for (Iterator<IntentionActionWithTextCaching> iterator = cachedActions.iterator(); iterator.hasNext(); ) {
        IntentionActionWithTextCaching cachedAction = iterator.next();
        IntentionAction action = cachedAction.getAction();
        Pair<PsiFile, Editor> applicableIn = ShowIntentionActionsHandler
          .chooseBetweenHostAndInjected(myFile, myEditor, injectedFile, (f, e) -> ShowIntentionActionsHandler.availableFor(f, e, action));
        if (applicableIn  == null) {
          iterator.remove();
          changed = true;
        }
      }
    }

    Set<IntentionActionWithTextCaching> wrappedNew = new ObjectOpenCustomHashSet<>(newDescriptors.size(), ACTION_TEXT_AND_CLASS_EQUALS);
    for (HighlightInfo.IntentionActionDescriptor descriptor : newDescriptors) {
      final IntentionAction action = descriptor.getAction();
      if (element != null &&
          element != hostElement &&
          (!shouldCallIsAvailable || ShowIntentionActionsHandler.availableFor(injectedFile, injectedEditor, action))) {
        IntentionActionWithTextCaching cachedAction = wrapAction(descriptor, element, injectedFile, injectedEditor);
        wrappedNew.add(cachedAction);
        changed |= cachedActions.add(cachedAction);
      }
      else if (hostElement != null && (!shouldCallIsAvailable || ShowIntentionActionsHandler.availableFor(myFile, myEditor, action))) {
        IntentionActionWithTextCaching cachedAction = wrapAction(descriptor, hostElement, myFile, myEditor);
        wrappedNew.add(cachedAction);
        changed |= cachedActions.add(cachedAction);
      }
    }
    for (Iterator<IntentionActionWithTextCaching> iterator = cachedActions.iterator(); iterator.hasNext(); ) {
      IntentionActionWithTextCaching cachedAction = iterator.next();
      if (!wrappedNew.contains(cachedAction)) {
        // action disappeared
        iterator.remove();
        changed = true;
      }
    }
    return changed;
  }

  @NotNull
  IntentionActionWithTextCaching wrapAction(@NotNull HighlightInfo.IntentionActionDescriptor descriptor,
                                            @NotNull PsiElement element,
                                            @NotNull  PsiFile containingFile,
                                            @Nullable Editor containingEditor) {
    IntentionActionWithTextCaching cachedAction = new IntentionActionWithTextCaching(descriptor, (cached, action) -> {
      if (action instanceof QuickFixWrapper) {
        // remove only inspection fixes after invocation,
        // since intention actions might be still available
        removeActionFromCached(cached);
        markInvoked(action);
      }
    });
    final List<IntentionAction> options = descriptor.getOptions(element, containingEditor);
    if (options == null) return cachedAction;
    for (IntentionAction option : options) {
      Editor editor = ObjectUtils.chooseNotNull(myEditor, containingEditor);
      if (editor == null) continue;
      Pair<PsiFile, Editor> availableIn = ShowIntentionActionsHandler
        .chooseBetweenHostAndInjected(myFile, editor, containingFile, (f, e) -> ShowIntentionActionsHandler.availableFor(f, e, option));
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
    // (the highlighting will process the whole file and remove the no more available action from the list automatically - but it's may be too long)
    myErrorFixes.remove(action);
    myGutters.remove(action);
    myInspectionFixes.remove(action);
    myIntentions.remove(action);
    myNotifications.remove(action);
  }

  @NotNull
  public List<IntentionActionWithTextCaching> getAllActions() {
    List<IntentionActionWithTextCaching> result = new ArrayList<>(myErrorFixes);
    result.addAll(myInspectionFixes);
    result.addAll(myIntentions);
    result.addAll(myGutters);
    result.addAll(myNotifications);
    result = DumbService.getInstance(myProject).filterByDumbAwareness(result);
    result.sort((o1, o2) -> {
      int weight1 = getWeight(o1);
      int weight2 = getWeight(o2);
      if (weight1 != weight2) {
        return weight2 - weight1;
      }
      return o1.compareTo(o2);
    });
    return result;
  }

  private int getWeight(@NotNull IntentionActionWithTextCaching action) {
    IntentionAction a = action.getAction();
    int group = getGroup(action).getPriority();
    while (a instanceof IntentionActionDelegate && !(a instanceof PriorityAction)) {
      a = ((IntentionActionDelegate)a).getDelegate();
    }
    if (a instanceof PriorityAction) {
      return group + getPriorityWeight(((PriorityAction)a).getPriority());
    }
    if (a instanceof SuppressIntentionActionFromFix) {
      if (((SuppressIntentionActionFromFix)a).isShouldBeAppliedToInjectionHost() == ThreeState.NO) {
        return group - 1;
      }
    }
    return group;
  }

  private static int getPriorityWeight(PriorityAction.Priority priority) {
    switch (priority) {
      case TOP:
        return 20;
      case HIGH:
        return 3;
      case LOW:
        return -3;
      case ERROR_FIX_LESS_IMPORTANT_THAN_INSPECTION_FIX:
        return IntentionGroup.INSPECTION.getPriority() - IntentionGroup.ERROR.getPriority() - 1;
      default:
        return 0;
    }
  }

  @NotNull
  public IntentionGroup getGroup(@NotNull IntentionActionWithTextCaching action) {
    if (myErrorFixes.contains(action)) {
      return IntentionGroup.ERROR;
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
    return IntentionGroup.OTHER;
  }

  @NotNull
  public Icon getIcon(@NotNull IntentionActionWithTextCaching value) {
    if (value.getIcon() != null) {
      return value.getIcon();
    }

    IntentionAction action = value.getAction();

    while (action instanceof IntentionActionDelegate) {
      action = ((IntentionActionDelegate)action).getDelegate();
    }
    Object iconable = action;
    //custom icon
    if (action instanceof QuickFixWrapper) {
      iconable = ((QuickFixWrapper)action).getFix();
    }

    if (iconable instanceof Iconable) {
      final Icon icon = ((Iconable)iconable).getIcon(0);
      if (icon != null) {
        return icon;
      }
    }

    if (IntentionManagerSettings.getInstance().isShowLightBulb(action)) {
      return myErrorFixes.contains(value) ? AllIcons.Actions.QuickfixBulb
                                          : myInspectionFixes.contains(value) ? AllIcons.Actions.IntentionBulb :
                                            AllIcons.Actions.RealIntentionBulb;
    }
    else {
      if (myErrorFixes.contains(value)) return AllIcons.Actions.QuickfixOffBulb;
      return IconLoader.getDisabledIcon(AllIcons.Actions.RealIntentionBulb);
    }
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

  private static class IntentionActionWithTextCachingHashingStrategy implements HashingStrategy<IntentionActionWithTextCaching>, Hash.Strategy<IntentionActionWithTextCaching> {
    @Override
    public int hashCode(final IntentionActionWithTextCaching object) {
      return object == null ? 0 : object.getText().hashCode();
    }

    @Override
    public boolean equals(IntentionActionWithTextCaching o1, IntentionActionWithTextCaching o2) {
      return o1 == o2 || (o1 != null && o2 != null && getActionClass(o1) == getActionClass(o2) && o1.getText().equals(o2.getText()));
    }

    private static Class<? extends IntentionAction> getActionClass(IntentionActionWithTextCaching o1) {
      return IntentionActionDelegate.unwrap(o1.getAction()).getClass();
    }
  }
}
