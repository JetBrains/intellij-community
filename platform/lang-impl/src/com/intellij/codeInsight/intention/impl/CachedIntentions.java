// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass;
import com.intellij.codeInsight.intention.*;
import com.intellij.codeInsight.intention.impl.config.IntentionManagerSettings;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.SuppressIntentionActionFromFix;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public class CachedIntentions {
  private static final Logger LOG = Logger.getInstance(CachedIntentions.class);

  private final Set<IntentionActionWithTextCaching> myCachedIntentions =
    ConcurrentCollectionFactory.createConcurrentSet(ACTION_TEXT_AND_CLASS_EQUALS);
  private final Set<IntentionActionWithTextCaching> myCachedErrorFixes =
    ConcurrentCollectionFactory.createConcurrentSet(ACTION_TEXT_AND_CLASS_EQUALS);
  private final Set<IntentionActionWithTextCaching> myCachedInspectionFixes =
    ConcurrentCollectionFactory.createConcurrentSet(ACTION_TEXT_AND_CLASS_EQUALS);
  private final Set<IntentionActionWithTextCaching> myCachedGutters =
    ConcurrentCollectionFactory.createConcurrentSet(ACTION_TEXT_AND_CLASS_EQUALS);
  private final Set<IntentionActionWithTextCaching> myCachedNotifications =
    ConcurrentCollectionFactory.createConcurrentSet(ACTION_TEXT_AND_CLASS_EQUALS);

  public Set<IntentionActionWithTextCaching> getIntentions() {
    return myCachedIntentions;
  }

  public Set<IntentionActionWithTextCaching> getErrorFixes() {
    return myCachedErrorFixes;
  }

  public Set<IntentionActionWithTextCaching> getInspectionFixes() {
    return myCachedInspectionFixes;
  }

  public Set<IntentionActionWithTextCaching> getGutters() {
    return myCachedGutters;
  }

  public Set<IntentionActionWithTextCaching> getNotifications() {
    return myCachedNotifications;
  }

  @Nullable
  private final Editor myEditor;
  private final PsiFile myFile;
  private final Project myProject;

  @Nullable
  public Editor getEditor() {
    return myEditor;
  }

  public PsiFile getFile() {
    return myFile;
  }

  public Project getProject() {
    return myProject;
  }

  public static CachedIntentions create(Project project, PsiFile file, Editor editor, @NotNull ShowIntentionsPass.IntentionsInfo intentions) {
    CachedIntentions res = new CachedIntentions(project, file, editor);
    res.wrapAndUpdateActions(intentions, false);
    return res;
  }

  public CachedIntentions(Project project, PsiFile file, Editor editor) {
    myProject = project;
    myFile = file;
    myEditor = editor;
  }

  private static final TObjectHashingStrategy<IntentionActionWithTextCaching> ACTION_TEXT_AND_CLASS_EQUALS = new TObjectHashingStrategy<IntentionActionWithTextCaching>() {
    @Override
    public int computeHashCode(final IntentionActionWithTextCaching object) {
      return object.getText().hashCode();
    }

    @Override
    public boolean equals(final IntentionActionWithTextCaching o1, final IntentionActionWithTextCaching o2) {
      return getActionClass(o1) == getActionClass(o2) && o1.getText().equals(o2.getText());
    }

    private Class<? extends IntentionAction> getActionClass(IntentionActionWithTextCaching o1) {
      IntentionAction action = o1.getAction();
      if (action instanceof IntentionActionDelegate) {
        return ((IntentionActionDelegate)action).getDelegate().getClass();
      }
      return action.getClass();
    }
  };


  public boolean wrapAndUpdateActions(@NotNull ShowIntentionsPass.IntentionsInfo newInfo, boolean callUpdate) {
    boolean changed = wrapActionsTo(newInfo.errorFixesToShow, myCachedErrorFixes, callUpdate);
    changed |= wrapActionsTo(newInfo.inspectionFixesToShow, myCachedInspectionFixes, callUpdate);
    changed |= wrapActionsTo(newInfo.intentionsToShow, myCachedIntentions, callUpdate);
    changed |= wrapActionsTo(newInfo.guttersToShow, myCachedGutters, callUpdate);
    changed |= wrapActionsTo(newInfo.notificationActionsToShow, myCachedNotifications, callUpdate);
    return changed;
  }

  private boolean wrapActionsTo(@NotNull List<HighlightInfo.IntentionActionDescriptor> newDescriptors,
                                @NotNull Set<IntentionActionWithTextCaching> cachedActions,
                                boolean callUpdate) {
    boolean changed = false;
    if (myEditor == null) {
      LOG.assertTrue(!callUpdate);
      for (HighlightInfo.IntentionActionDescriptor descriptor : newDescriptors) {
        changed |= cachedActions.add(wrapAction(descriptor, myFile, myFile, null));
      }
    } else {
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
        element = InjectedLanguageUtil.findElementAtNoCommit(myFile, fileOffset);
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

      for (Iterator<IntentionActionWithTextCaching> iterator = cachedActions.iterator(); iterator.hasNext(); ) {
        IntentionActionWithTextCaching cachedAction = iterator.next();
        IntentionAction action = cachedAction.getAction();
        if (!ShowIntentionActionsHandler.availableFor(myFile, myEditor, action) &&
            (hostElement == element || element != null && !ShowIntentionActionsHandler.availableFor(injectedFile, injectedEditor, action))) {
          iterator.remove();
          changed = true;
        }
      }

      Set<IntentionActionWithTextCaching> wrappedNew =
        new THashSet<>(newDescriptors.size(), ACTION_TEXT_AND_CLASS_EQUALS);
      for (HighlightInfo.IntentionActionDescriptor descriptor : newDescriptors) {
        final IntentionAction action = descriptor.getAction();
        if (element != null &&
            element != hostElement &&
            (!callUpdate || ShowIntentionActionsHandler.availableFor(injectedFile, injectedEditor, action))) {
          IntentionActionWithTextCaching cachedAction = wrapAction(descriptor, element, injectedFile, injectedEditor);
          wrappedNew.add(cachedAction);
          changed |= cachedActions.add(cachedAction);
        }
        else if (hostElement != null && (!callUpdate || ShowIntentionActionsHandler.availableFor(myFile, myEditor, action))) {
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
    }
    return changed;
  }

  @NotNull
  IntentionActionWithTextCaching wrapAction(@NotNull HighlightInfo.IntentionActionDescriptor descriptor,
                                            @Nullable PsiElement element,
                                            @Nullable PsiFile containingFile,
                                            @Nullable Editor containingEditor) {
    IntentionActionWithTextCaching cachedAction = new IntentionActionWithTextCaching(descriptor, (cached, action)->{
      removeActionFromCached(cached);
      markInvoked(action);
    });
    if (element == null) return cachedAction;
    final List<IntentionAction> options = descriptor.getOptions(element, containingEditor);
    if (options == null) return cachedAction;
    for (IntentionAction option : options) {
      if (containingFile != null && containingEditor != null && myEditor != null) {
        if (!ShowIntentionActionsHandler.availableFor(containingFile, containingEditor, option)) {
          //if option is not applicable in injected fragment, check in host file context
          if (containingEditor == myEditor || !ShowIntentionActionsHandler.availableFor(myFile, myEditor, option)) {
            continue;
          }
        }
      }
      else if (!option.isAvailable(myProject, containingEditor, containingFile)) {
        // if option is not applicable in injected fragment, check in host file context
        if (containingEditor == myEditor || !option.isAvailable(myProject, myEditor, myFile)) {
          continue;
        }
      }
      IntentionActionWithTextCaching textCaching = new IntentionActionWithTextCaching(option);
      boolean isErrorFix = myCachedErrorFixes.contains(textCaching);
      if (isErrorFix) {
        cachedAction.addErrorFix(option);
      }
      boolean isInspectionFix = myCachedInspectionFixes.contains(textCaching);
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
    myCachedErrorFixes.remove(action);
    myCachedGutters.remove(action);
    myCachedInspectionFixes.remove(action);
    myCachedIntentions.remove(action);
    myCachedNotifications.remove(action);
  }

  public List<IntentionActionWithTextCaching> getAllAction() {
    List<IntentionActionWithTextCaching> result = new ArrayList<>(myCachedErrorFixes);
    result.addAll(myCachedInspectionFixes);
    result.addAll(myCachedIntentions);
    result.addAll(myCachedGutters);
    result.addAll(myCachedNotifications);
    result = DumbService.getInstance(myProject).filterByDumbAwareness(result);
    Collections.sort(result, (o1, o2) -> {
      int weight1 = getWeight(o1);
      int weight2 = getWeight(o2);
      if (weight1 != weight2) {
        return weight2 - weight1;
      }
      return o1.compareTo(o2);
    });
    return result;
  }

  private int getWeight(IntentionActionWithTextCaching action) {
    IntentionAction a = action.getAction();
    int group = getGroup(action);
    while (a instanceof IntentionActionDelegate) {
      a = ((IntentionActionDelegate)a).getDelegate();
    }
    if (a instanceof HighPriorityAction) {
      return group + 3;
    }
    if (a instanceof LowPriorityAction) {
      return group - 3;
    }
    if (a instanceof SuppressIntentionActionFromFix) {
      if (((SuppressIntentionActionFromFix)a).isShouldBeAppliedToInjectionHost() == ThreeState.NO) {
        return group - 1;
      }
    }
    if (a instanceof QuickFixWrapper) {
      final LocalQuickFix quickFix = ((QuickFixWrapper)a).getFix();
      if (quickFix instanceof HighPriorityAction) {
        return group + 3;
      }
      if (quickFix instanceof LowPriorityAction) {
        return group - 3;
      }
    }
    return group;
  }

  public int getGroup(IntentionActionWithTextCaching action) {
    if (myCachedErrorFixes.contains(action)) {
      return 20;
    }
    if (myCachedInspectionFixes.contains(action)) {
      return 10;
    }
    if (myCachedNotifications.contains(action)) {
      return 7;
    }
    if (myCachedGutters.contains(action)) {
      return 5;
    }
    if (action.getAction() instanceof EmptyIntentionAction) {
      return -10;
    }
    return 0;
  }

  public Icon getIcon(IntentionActionWithTextCaching value) {
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
      return myCachedErrorFixes.contains(value) ? AllIcons.Actions.QuickfixBulb
                                                                      : myCachedInspectionFixes.contains(value) ? AllIcons.Actions.IntentionBulb :
                                                                        AllIcons.Actions.RealIntentionBulb;
    }
    else {
      return myCachedErrorFixes.contains(value) ? AllIcons.Actions.QuickfixOffBulb : AllIcons.Actions.RealIntentionOffBulb;
    }
  }

  public boolean showBulb() {
    return ContainerUtil.exists(getAllAction(), info -> IntentionManagerSettings.getInstance().isShowLightBulb(info.getAction()));
  }

}
