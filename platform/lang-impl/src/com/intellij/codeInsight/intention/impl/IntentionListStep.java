/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.codeInsight.intention.EmptyIntentionAction;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInsight.intention.impl.config.IntentionActionWrapper;
import com.intellij.codeInsight.intention.impl.config.IntentionManagerSettings;
import com.intellij.codeInspection.IntentionWrapper;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.containers.ConcurrentHashSet;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
* @author cdr
*/
class IntentionListStep implements ListPopupStep<IntentionActionWithTextCaching>, SpeedSearchFilter<IntentionActionWithTextCaching> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.IntentionListStep");

  private final Set<IntentionActionWithTextCaching> myCachedIntentions = new ConcurrentHashSet<IntentionActionWithTextCaching>(ACTION_TEXT_AND_CLASS_EQUALS);
  private final Set<IntentionActionWithTextCaching> myCachedErrorFixes = new ConcurrentHashSet<IntentionActionWithTextCaching>(ACTION_TEXT_AND_CLASS_EQUALS);
  private final Set<IntentionActionWithTextCaching> myCachedInspectionFixes = new ConcurrentHashSet<IntentionActionWithTextCaching>(ACTION_TEXT_AND_CLASS_EQUALS);
  private final Set<IntentionActionWithTextCaching> myCachedGutters = new ConcurrentHashSet<IntentionActionWithTextCaching>(ACTION_TEXT_AND_CLASS_EQUALS);
  private final IntentionManagerSettings mySettings;
  @Nullable
  private final IntentionHintComponent myIntentionHintComponent;
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
      return o1.getAction().getClass() == o2.getAction().getClass() && o1.getText().equals(o2.getText());
    }
  };
  private Runnable myFinalRunnable;

  IntentionListStep(@Nullable IntentionHintComponent intentionHintComponent,
                    @NotNull ShowIntentionsPass.IntentionsInfo intentions,
                    @NotNull Editor editor,
                    @NotNull PsiFile file,
                    @NotNull Project project) {
    myIntentionHintComponent = intentionHintComponent;
    myEditor = editor;
    myFile = file;
    myProject = project;
    mySettings = IntentionManagerSettings.getInstance();
    updateActions(intentions);
  }

  //true if something changed
  boolean updateActions(@NotNull ShowIntentionsPass.IntentionsInfo intentions) {
    boolean changed = wrapActionsTo(intentions.errorFixesToShow, myCachedErrorFixes);
    changed |= wrapActionsTo(intentions.inspectionFixesToShow, myCachedInspectionFixes);
    changed |= wrapActionsTo(intentions.intentionsToShow, myCachedIntentions);
    changed |= wrapActionsTo(intentions.guttersToShow, myCachedGutters);
    return changed;
  }

  private boolean wrapActionsTo(@NotNull List<HighlightInfo.IntentionActionDescriptor> newDescriptors,
                                @NotNull Set<IntentionActionWithTextCaching> cachedActions) {
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

    boolean changed = false;
    for (Iterator<IntentionActionWithTextCaching> iterator = cachedActions.iterator(); iterator.hasNext();) {
      IntentionActionWithTextCaching cachedAction = iterator.next();
      IntentionAction action = cachedAction.getAction();
      if (!ShowIntentionActionsHandler.availableFor(myFile, myEditor, action)
        && (hostElement == element || element != null && !ShowIntentionActionsHandler.availableFor(injectedFile, injectedEditor, action))) {
        iterator.remove();
        changed = true;
      }
    }

    Set<IntentionActionWithTextCaching> wrappedNew = new THashSet<IntentionActionWithTextCaching>(newDescriptors.size(), ACTION_TEXT_AND_CLASS_EQUALS);
    for (HighlightInfo.IntentionActionDescriptor descriptor : newDescriptors) {
      final IntentionAction action = descriptor.getAction();
      if (element != null && element != hostElement && ShowIntentionActionsHandler.availableFor(injectedFile, injectedEditor, action)) {
        IntentionActionWithTextCaching cachedAction = wrapAction(descriptor, element, injectedFile, injectedEditor);
        wrappedNew.add(cachedAction);
        changed |= cachedActions.add(cachedAction);
      }
      else if (hostElement != null && ShowIntentionActionsHandler.availableFor(myFile, myEditor, action)) {
        IntentionActionWithTextCaching cachedAction = wrapAction(descriptor, hostElement, myFile, myEditor);
        wrappedNew.add(cachedAction);
        changed |= cachedActions.add(cachedAction);
      }
    }
    for (Iterator<IntentionActionWithTextCaching> iterator = cachedActions.iterator(); iterator.hasNext();) {
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
                                            @NotNull PsiFile containingFile,
                                            @NotNull Editor containingEditor) {
    IntentionActionWithTextCaching cachedAction = new IntentionActionWithTextCaching(descriptor);
    final List<IntentionAction> options = descriptor.getOptions(element, containingEditor);
    if (options == null) return cachedAction;
    for (IntentionAction option : options) {
      if (!option.isAvailable(myProject, containingEditor, containingFile)) {
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
    if (finalChoice && !(action.getAction() instanceof EmptyIntentionAction)) {
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

  private void applyAction(final IntentionActionWithTextCaching cachedAction) {
    myFinalRunnable = new Runnable() {
      @Override
      public void run() {
        HintManager.getInstance().hideAllHints();
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            if (myProject.isDisposed()) return;
            PsiDocumentManager.getInstance(myProject).commitAllDocuments();
            final PsiFile file = PsiUtilBase.getPsiFileInEditor(myEditor, myProject);
            if (file == null) {
              return;
            }

            ShowIntentionActionsHandler.chooseActionAndInvoke(file, myEditor, cachedAction.getAction(), cachedAction.getText());
          }
        });
      }
    };
  }

  IntentionListStep getSubStep(final IntentionActionWithTextCaching action, final String title) {
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

    return new IntentionListStep(myIntentionHintComponent, intentions,myEditor, myFile, myProject){
      @Override
      public String getTitle() {
        return title;
      }
    };
  }

  private static Icon getIcon(IntentionAction optionIntention) {
    return optionIntention instanceof Iconable ? ((Iconable)optionIntention).getIcon(0) : null;
  }

  @Override
  public boolean hasSubstep(final IntentionActionWithTextCaching action) {
    return action.getOptionIntentions().size() + action.getOptionErrorFixes().size() > 0;
  }

  @Override
  @NotNull
  public List<IntentionActionWithTextCaching> getValues() {
    List<IntentionActionWithTextCaching> result = new ArrayList<IntentionActionWithTextCaching>(myCachedErrorFixes);
    result.addAll(myCachedInspectionFixes);
    result.addAll(myCachedIntentions);
    result.addAll(myCachedGutters);
    Collections.sort(result, new Comparator<IntentionActionWithTextCaching>() {
      @Override
      public int compare(final IntentionActionWithTextCaching o1, final IntentionActionWithTextCaching o2) {
        int weight1 = getWeight(o1);
        int weight2 = getWeight(o2);
        if (weight1 != weight2) {
          return weight2 - weight1;
        }
        return Comparing.compare(o1.getText(), o2.getText());
      }
    });
    return result;
  }

  private int getWeight(IntentionActionWithTextCaching action) {
    IntentionAction a = action.getAction();
    int group = getGroup(action);
    if (a instanceof IntentionActionWrapper) {
      a = ((IntentionActionWrapper)a).getDelegate();
    }
    if (a instanceof IntentionWrapper) {
      a = ((IntentionWrapper)a).getAction();
    }
    if (a instanceof HighPriorityAction) {
      return group + 3;
    }
    if (a instanceof LowPriorityAction) {
      return group - 3;
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
    } else if (action instanceof IntentionActionWrapper) {
      iconable = ((IntentionActionWrapper)action).getDelegate();
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
