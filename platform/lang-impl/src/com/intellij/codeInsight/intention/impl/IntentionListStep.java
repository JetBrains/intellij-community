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

package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.*;
import com.intellij.codeInsight.intention.impl.config.IntentionManagerSettings;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.SuppressIntentionActionFromFix;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.*;
import java.util.stream.Collectors;

/**
* @author cdr
*/
public class IntentionListStep implements ListPopupStep<IntentionActionWithTextCaching>, SpeedSearchFilter<IntentionActionWithTextCaching> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.IntentionListStep");

  private final Set<IntentionActionWithTextCaching> myCachedIntentions = ContainerUtil.newConcurrentSet(ACTION_TEXT_AND_CLASS_EQUALS);
  private final Set<IntentionActionWithTextCaching> myCachedErrorFixes = ContainerUtil.newConcurrentSet(ACTION_TEXT_AND_CLASS_EQUALS);
  private final Set<IntentionActionWithTextCaching> myCachedInspectionFixes = ContainerUtil.newConcurrentSet(ACTION_TEXT_AND_CLASS_EQUALS);
  private final Set<IntentionActionWithTextCaching> myCachedGutters = ContainerUtil.newConcurrentSet(ACTION_TEXT_AND_CLASS_EQUALS);
  private final Set<IntentionActionWithTextCaching> myCachedNotifications = ContainerUtil.newConcurrentSet(ACTION_TEXT_AND_CLASS_EQUALS);
  private final IntentionManagerSettings mySettings;
  @Nullable
  private final IntentionHintComponent myIntentionHintComponent;
  @Nullable
  private final Editor myEditor;
  private final PsiFile myFile;
  private final Project myProject;
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
  private Runnable myFinalRunnable;

  public IntentionListStep(@Nullable IntentionHintComponent intentionHintComponent,
                           @NotNull ShowIntentionsPass.IntentionsInfo intentions,
                           @Nullable Editor editor,
                           @NotNull PsiFile file,
                           @NotNull Project project) {
    this(intentionHintComponent, editor, file, project);
    wrapAndUpdateActions(intentions, false); // when create bulb do not update actions again since it would impede the EDT
  }

  IntentionListStep(@Nullable IntentionHintComponent intentionHintComponent,
                    @Nullable Editor editor,
                    @NotNull PsiFile file,
                    @NotNull Project project) {
    myIntentionHintComponent = intentionHintComponent;
    myEditor = editor;
    myFile = file;
    myProject = project;
    mySettings = IntentionManagerSettings.getInstance();
  }

  //true if something changed
  boolean wrapAndUpdateActions(@NotNull ShowIntentionsPass.IntentionsInfo newInfo, boolean callUpdate) {
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

  @Override
  public String getTitle() {
    return null;
  }

  @Override
  public boolean isSelectable(final IntentionActionWithTextCaching action) {
    return true;
  }

  @Override
  public PopupStep onChosen(final IntentionActionWithTextCaching action, final boolean finalChoice) {
    if (finalChoice && !(action.getAction() instanceof AbstractEmptyIntentionAction)) {
      applyAction(action);
      return FINAL_CHOICE;
    }

    if (hasSubstep(action)) {
      return getSubStep(action, action.getToolName());
    }

    return FINAL_CHOICE;
  }

  @Override
  public Runnable getFinalRunnable() {
    return myFinalRunnable;
  }

  private void applyAction(@NotNull IntentionActionWithTextCaching cachedAction) {
    myFinalRunnable = () -> {
      HintManager.getInstance().hideAllHints();
      if (myProject.isDisposed() || myEditor != null && myEditor.isDisposed()) return;
      if (DumbService.isDumb(myProject) && !DumbService.isDumbAware(cachedAction)) {
        DumbService.getInstance(myProject).showDumbModeNotification(cachedAction.getText() + " is not available during indexing");
        return;
      }

      PsiDocumentManager.getInstance(myProject).commitAllDocuments();

      PsiFile file = myEditor != null ? PsiUtilBase.getPsiFileInEditor(myEditor, myProject) : myFile;
      if (file == null) {
        return;
      }

      ShowIntentionActionsHandler.chooseActionAndInvoke(file, myEditor, cachedAction.getAction(), cachedAction.getText(), myProject);
    };
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

  @NotNull
  IntentionListStep getSubStep(@NotNull IntentionActionWithTextCaching action, final String title) {
    ShowIntentionsPass.IntentionsInfo intentions = new ShowIntentionsPass.IntentionsInfo();
    for (final IntentionAction optionIntention : action.getOptionIntentions()) {
      intentions.intentionsToShow.add(new HighlightInfo.IntentionActionDescriptor(optionIntention, getIcon(optionIntention)));
    }
    for (final IntentionAction optionFix : action.getOptionErrorFixes()) {
      intentions.errorFixesToShow.add(new HighlightInfo.IntentionActionDescriptor(optionFix, getIcon(optionFix)));
    }
    for (final IntentionAction optionFix : action.getOptionInspectionFixes()) {
      intentions.inspectionFixesToShow.add(new HighlightInfo.IntentionActionDescriptor(optionFix, getIcon(optionFix)));
    }

    return new IntentionListStep(myIntentionHintComponent, intentions, myEditor, myFile, myProject){
      @Override
      public String getTitle() {
        return title;
      }
    };
  }

  private static Icon getIcon(IntentionAction optionIntention) {
    return optionIntention instanceof Iconable ? ((Iconable)optionIntention).getIcon(0) : null;
  }

  @TestOnly
  public Map<IntentionAction, List<IntentionAction>> getActionsWithSubActions() {
    Map<IntentionAction, List<IntentionAction>> result = ContainerUtil.newLinkedHashMap();

    for (IntentionActionWithTextCaching cached : getValues()) {
      IntentionAction action = cached.getAction();
      if (ShowIntentionActionsHandler.chooseFileForAction(myFile, myEditor, action) == null) continue;

      List<IntentionActionWithTextCaching> subActions = getSubStep(cached, cached.getToolName()).getValues();
      List<IntentionAction> options = subActions.stream()
          .map(IntentionActionWithTextCaching::getAction)
          .filter(option -> ShowIntentionActionsHandler.chooseFileForAction(myFile, myEditor, option) != null)
          .collect(Collectors.toList());
      result.put(action, options);
    }
    return result;
  }

  @Override
  public boolean hasSubstep(final IntentionActionWithTextCaching action) {
    return action.getOptionIntentions().size() + action.getOptionErrorFixes().size() > 0;
  }

  @Override
  @NotNull
  public List<IntentionActionWithTextCaching> getValues() {
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
    if (a instanceof IntentionActionDelegate) {
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

  private int getGroup(IntentionActionWithTextCaching action) {
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

  @Override
  @NotNull
  public String getTextFor(final IntentionActionWithTextCaching action) {
    final String text = action.getAction().getText();
    if (LOG.isDebugEnabled() && text.startsWith("<html>")) {
      LOG.info("IntentionAction.getText() returned HTML: action=" + action + " text=" + text);
    }
    return text;
  }

  @Override
  public Icon getIconFor(final IntentionActionWithTextCaching value) {
    if (value.getIcon() != null) {
      return value.getIcon();
    }

    final IntentionAction action = value.getAction();

    Object iconable = action;
    //custom icon
    if (action instanceof QuickFixWrapper) {
      iconable = ((QuickFixWrapper)action).getFix();
    }
    else if (action instanceof IntentionActionDelegate) {
      iconable = ((IntentionActionDelegate)action).getDelegate();
    }

    if (iconable instanceof Iconable) {
      final Icon icon = ((Iconable)iconable).getIcon(0);
      if (icon != null) {
        return icon;
      }
    }

    if (mySettings.isShowLightBulb(action)) {
      return myCachedErrorFixes.contains(value) ? AllIcons.Actions.QuickfixBulb
             : myCachedInspectionFixes.contains(value) ? AllIcons.Actions.IntentionBulb :
               AllIcons.Actions.RealIntentionBulb;
    }
    else {
      return myCachedErrorFixes.contains(value) ? AllIcons.Actions.QuickfixOffBulb : AllIcons.Actions.RealIntentionOffBulb;
    }
  }

  @Override
  public void canceled() {
    if (myIntentionHintComponent != null) {
      myIntentionHintComponent.canceled(this);
    }
  }

  @Override
  public int getDefaultOptionIndex() { return 0; }
  @Override
  public ListSeparator getSeparatorAbove(final IntentionActionWithTextCaching value) {
    List<IntentionActionWithTextCaching> values = getValues();
    int index = values.indexOf(value);
    if (index <= 0) return null;
    IntentionActionWithTextCaching prev = values.get(index - 1);

    if (getGroup(value) != getGroup(prev)) {
      return new ListSeparator();
    }
    return null;
  }
  @Override
  public boolean isMnemonicsNavigationEnabled() { return false; }
  @Override
  public MnemonicNavigationFilter<IntentionActionWithTextCaching> getMnemonicNavigationFilter() { return null; }
  @Override
  public boolean isSpeedSearchEnabled() { return true; }
  @Override
  public boolean isAutoSelectionEnabled() { return false; }
  @Override
  public SpeedSearchFilter<IntentionActionWithTextCaching> getSpeedSearchFilter() { return this; }

  //speed search filter
  @Override
  public boolean canBeHidden(final IntentionActionWithTextCaching value) { return true;}
  @Override
  public String getIndexedString(final IntentionActionWithTextCaching value) { return getTextFor(value);}
}
