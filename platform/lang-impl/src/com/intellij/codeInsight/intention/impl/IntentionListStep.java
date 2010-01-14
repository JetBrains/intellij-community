/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PreferredAction;
import com.intellij.codeInsight.intention.impl.config.IntentionManagerSettings;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiUtilBase;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

/**
* @author cdr
*/
class IntentionListStep implements ListPopupStep<IntentionActionWithTextCaching>, SpeedSearchFilter<IntentionActionWithTextCaching> {
  private final Set<IntentionActionWithTextCaching> myCachedIntentions = new THashSet<IntentionActionWithTextCaching>(ACTION_TEXT_AND_CLASS_EQUALS);
  private final Set<IntentionActionWithTextCaching> myCachedErrorFixes = new THashSet<IntentionActionWithTextCaching>(ACTION_TEXT_AND_CLASS_EQUALS);
  private final Set<IntentionActionWithTextCaching> myCachedInspectionFixes = new THashSet<IntentionActionWithTextCaching>(ACTION_TEXT_AND_CLASS_EQUALS);
  private final Set<IntentionActionWithTextCaching> myCachedGutters = new THashSet<IntentionActionWithTextCaching>(ACTION_TEXT_AND_CLASS_EQUALS);
  private final IntentionManagerSettings mySettings;
  private final IntentionHintComponent myIntentionHintComponent;
  private final Editor myEditor;
  private final PsiFile myFile;
  private final Project myProject;
  private static final TObjectHashingStrategy<IntentionActionWithTextCaching> ACTION_TEXT_AND_CLASS_EQUALS = new TObjectHashingStrategy<IntentionActionWithTextCaching>() {
    public int computeHashCode(final IntentionActionWithTextCaching object) {
      return object.getText().hashCode();
    }

    public boolean equals(final IntentionActionWithTextCaching o1, final IntentionActionWithTextCaching o2) {
      return o1.getAction().getClass() == o2.getAction().getClass() && o1.getText().equals(o2.getText());
    }
  };
  private Runnable myFinalRunnable;

  IntentionListStep(IntentionHintComponent intentionHintComponent, ShowIntentionsPass.IntentionsInfo intentions, Editor editor, PsiFile file,
                    Project project) {
    myIntentionHintComponent = intentionHintComponent;
    myEditor = editor;
    myFile = file;
    myProject = project;
    mySettings = IntentionManagerSettings.getInstance();
    updateActions(intentions);
  }

  //true if something changed
  boolean updateActions(ShowIntentionsPass.IntentionsInfo intentions) {
    boolean result = wrapActionsTo(intentions.errorFixesToShow, myCachedErrorFixes);
    result &= wrapActionsTo(intentions.inspectionFixesToShow, myCachedInspectionFixes);
    result &= wrapActionsTo(intentions.intentionsToShow, myCachedIntentions);
    result &= wrapActionsTo(intentions.guttersToShow, myCachedGutters);
    return !result;
  }

  private boolean wrapActionsTo(final List<HighlightInfo.IntentionActionDescriptor> descriptors, final Set<IntentionActionWithTextCaching> cachedActions) {
    final int caretOffset = myEditor.getCaretModel().getOffset();
    final int fileOffset = caretOffset > 0 && caretOffset == myFile.getTextLength() ? caretOffset - 1 : caretOffset;
    PsiElement element;
    if (myFile instanceof PsiCompiledElement) {
      element = myFile;
    }
    else if (PsiDocumentManager.getInstance(myProject).isUncommited(myEditor.getDocument())) {
      //???
      FileViewProvider viewProvider = myFile.getViewProvider();
      element = viewProvider.findElementAt(fileOffset, viewProvider.getBaseLanguage());
    }
    else {
      element = InjectedLanguageUtil.findElementAtNoCommit(myFile, fileOffset);
    }
    boolean result = true;
    for (HighlightInfo.IntentionActionDescriptor descriptor : descriptors) {
      IntentionAction action = descriptor.getAction();
      IntentionActionWithTextCaching cachedAction = new IntentionActionWithTextCaching(action, descriptor.getDisplayName(), descriptor.getIcon());
      result &= !cachedActions.add(cachedAction);
      if (element == null) continue;
      final List<IntentionAction> options = descriptor.getOptions(element);
      if (options != null) {
        for (IntentionAction option : options) {
          if (!option.isAvailable(myProject, myEditor, element.getContainingFile())) continue;
          IntentionActionWithTextCaching textCaching = new IntentionActionWithTextCaching(option, option.getText());
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
      }
    }
    result &= removeInvalidActions(cachedActions, element);
    return result;
  }

  private boolean removeInvalidActions(final Collection<IntentionActionWithTextCaching> cachedActions, final PsiElement element) {
    boolean result = true;
    Iterator<IntentionActionWithTextCaching> iterator = cachedActions.iterator();
    while (iterator.hasNext()) {
      IntentionActionWithTextCaching cachedAction = iterator.next();
      IntentionAction action = cachedAction.getAction();
      Pair<PsiFile,Editor> place = ShowIntentionActionsHandler.availableFor(myFile, myEditor, action, element);
      if (place == null) {
        iterator.remove();
        result = false;
      }
    }
    return result;
  }

  public String getTitle() {
    return null;
  }

  public boolean isSelectable(final IntentionActionWithTextCaching action) {
    return true;
  }

  public PopupStep onChosen(final IntentionActionWithTextCaching action, final boolean finalChoice) {
    if (finalChoice && !(action.getAction() instanceof EmptyIntentionAction)) {
      applyAction(action);
      return FINAL_CHOICE;
    }

    if (hasSubstep(action)) {
      return getSubStep(action);
    }

    return FINAL_CHOICE;
  }

  public Runnable getFinalRunnable() {
    return myFinalRunnable;
  }

  private void applyAction(final IntentionActionWithTextCaching cachedAction) {
    myFinalRunnable = new Runnable() {
      public void run() {
        HintManager.getInstance().hideAllHints();
        ApplicationManager.getApplication().invokeLater(new Runnable() {
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

  private PopupStep getSubStep(final IntentionActionWithTextCaching action) {
    ShowIntentionsPass.IntentionsInfo intentions = new ShowIntentionsPass.IntentionsInfo();
    for (final IntentionAction optionIntention : action.getOptionIntentions()) {
      intentions.intentionsToShow.add(new HighlightInfo.IntentionActionDescriptor(optionIntention, null));
    }
    for (final IntentionAction optionFix : action.getOptionErrorFixes()) {
      intentions.errorFixesToShow.add(new HighlightInfo.IntentionActionDescriptor(optionFix, null));
    }
    for (final IntentionAction optionFix : action.getOptionInspectionFixes()) {
      intentions.inspectionFixesToShow.add(new HighlightInfo.IntentionActionDescriptor(optionFix, null));
    }

    return new IntentionListStep(myIntentionHintComponent, intentions,myEditor, myFile, myProject){
      public String getTitle() {
        return action.getToolName();
      }
    };
  }

  public boolean hasSubstep(final IntentionActionWithTextCaching action) {
    return action.getOptionIntentions().size() + action.getOptionErrorFixes().size() > 0;
  }

  @NotNull
  public List<IntentionActionWithTextCaching> getValues() {
    List<IntentionActionWithTextCaching> result = new ArrayList<IntentionActionWithTextCaching>(myCachedErrorFixes);
    result.addAll(myCachedInspectionFixes);
    result.addAll(myCachedIntentions);
    result.addAll(myCachedGutters);
    Collections.sort(result, new Comparator<IntentionActionWithTextCaching>() {
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
    if (action.getAction() instanceof PreferredAction) {
      return 3;
    }
    else if (myCachedErrorFixes.contains(action)) {
      return 2;
    }
    else if (myCachedInspectionFixes.contains(action)) {
      return 1;
    }
    else if (action.getAction() instanceof EmptyIntentionAction) {
      return -1;
    }
    else {
      return 0;
    }
  }

  @NotNull
  public String getTextFor(final IntentionActionWithTextCaching action) {
    return action.getAction().getText();
  }

  public Icon getIconFor(final IntentionActionWithTextCaching value) {
    if (value.getIcon() != null) {
      return value.getIcon();
    }

    final IntentionAction action = value.getAction();

    //custom icon
    if (action instanceof QuickFixWrapper) {
      final QuickFixWrapper quickFix = (QuickFixWrapper)action;
      if (quickFix.getFix() instanceof Iconable) {
        final Icon icon = ((Iconable)quickFix.getFix()).getIcon(0);
        if (icon != null) {
          return icon;
        }
      }
    }

    if (mySettings.isShowLightBulb(action)) {
      return myCachedErrorFixes.contains(value) ? IntentionHintComponent.ourQuickFixIcon
             : myCachedInspectionFixes.contains(value) ? IntentionHintComponent.ourBulbIcon :
               IntentionHintComponent.ourIntentionIcon;
    }
    else {
      return myCachedErrorFixes.contains(value) ? IntentionHintComponent.ourQuickFixOffIcon : IntentionHintComponent.ourIntentionOffIcon;
    }
  }

  public void canceled() {
    myIntentionHintComponent.canceled(this);
  }

  public int getDefaultOptionIndex() { return 0; }
  public ListSeparator getSeparatorAbove(final IntentionActionWithTextCaching value) {
    List<IntentionActionWithTextCaching> values = getValues();
    int index = values.indexOf(value);
    if (index == 0) return null;
    IntentionActionWithTextCaching prev = values.get(index - 1);

    if (getWeight(value) != getWeight(prev)) {
      return new ListSeparator();
    }
    return null;
  }
  public boolean isMnemonicsNavigationEnabled() { return false; }
  public MnemonicNavigationFilter<IntentionActionWithTextCaching> getMnemonicNavigationFilter() { return null; }
  public boolean isSpeedSearchEnabled() { return true; }
  public boolean isAutoSelectionEnabled() { return false; }
  public SpeedSearchFilter<IntentionActionWithTextCaching> getSpeedSearchFilter() { return this; }

  //speed search filter
  public boolean canBeHidden(final IntentionActionWithTextCaching value) { return true;}
  public String getIndexedString(final IntentionActionWithTextCaching value) { return getTextFor(value);}
}
