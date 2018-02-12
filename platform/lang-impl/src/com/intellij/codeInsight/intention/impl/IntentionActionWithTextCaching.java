/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.openapi.actionSystem.ShortcutProvider;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
* @author cdr
*/
public class IntentionActionWithTextCaching implements Comparable<IntentionActionWithTextCaching>, PossiblyDumbAware, ShortcutProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.IntentionActionWithTextCaching");
  private final List<IntentionAction> myOptionIntentions = new ArrayList<>();
  private final List<IntentionAction> myOptionErrorFixes = new ArrayList<>();
  private final List<IntentionAction> myOptionInspectionFixes = new ArrayList<>();
  private final String myText;
  private final IntentionAction myAction;
  private final String myDisplayName;
  private final Icon myIcon;

  IntentionActionWithTextCaching(@NotNull IntentionAction action){
    this(action, action.getText(), null, null);
  }

  IntentionActionWithTextCaching(@NotNull HighlightInfo.IntentionActionDescriptor descriptor, @Nullable BiConsumer<IntentionActionWithTextCaching,IntentionAction> markInvoked){
    this(descriptor.getAction(), descriptor.getDisplayName(), descriptor.getIcon(), markInvoked);
  }

  private IntentionActionWithTextCaching(@NotNull IntentionAction action, String displayName, @Nullable Icon icon, @Nullable BiConsumer<IntentionActionWithTextCaching, IntentionAction> markInvoked) {
    myIcon = icon;
    myText = action.getText();
    // needed for checking errors in user written actions
    //noinspection ConstantConditions
    LOG.assertTrue(myText != null, "action "+action.getClass()+" text returned null");
    myAction = new MyIntentionAction(action, markInvoked);
    myDisplayName = displayName;
  }

  @NotNull
  String getText() {
    return myText;
  }

  void addIntention(@NotNull IntentionAction action) {
    myOptionIntentions.add(action);
  }
  void addErrorFix(@NotNull IntentionAction action) {
    myOptionErrorFixes.add(action);
  }
  void addInspectionFix(@NotNull  IntentionAction action) {
    myOptionInspectionFixes.add(action);
  }

  @NotNull
  public IntentionAction getAction() {
    return myAction;
  }

  @NotNull
  List<IntentionAction> getOptionIntentions() {
    return myOptionIntentions;
  }

  @NotNull
  List<IntentionAction> getOptionErrorFixes() {
    return myOptionErrorFixes;
  }

  @NotNull
  List<IntentionAction> getOptionInspectionFixes() {
    return myOptionInspectionFixes;
  }

  String getToolName() {
    return myDisplayName;
  }

  @NotNull
  public String toString() {
    return getText();
  }

  @Override
  public int compareTo(@NotNull final IntentionActionWithTextCaching other) {
    if (myAction instanceof Comparable) {
      //noinspection unchecked
      return ((Comparable)myAction).compareTo(other.getAction());
    }
    if (other.getAction() instanceof Comparable) {
      //noinspection unchecked
      return -((Comparable)other.getAction()).compareTo(myAction);
    }
    return Comparing.compare(getText(), other.getText());
  }

  Icon getIcon() {
    return myIcon;
  }

  @Override
  public boolean isDumbAware() {
    return DumbService.isDumbAware(myAction);
  }

  @Nullable
  @Override
  public ShortcutSet getShortcut() {
    return myAction instanceof ShortcutProvider ? ((ShortcutProvider)myAction).getShortcut() : null;
  }

  // IntentionAction which wraps the original action and then marks it as executed to hide it from the popup to avoid invoking it twice accidentally
  private class MyIntentionAction implements IntentionAction, IntentionActionDelegate, Comparable<MyIntentionAction>, ShortcutProvider {
    private final IntentionAction myAction;
    private final BiConsumer<IntentionActionWithTextCaching, IntentionAction> myMarkInvoked;

    MyIntentionAction(IntentionAction action, BiConsumer<IntentionActionWithTextCaching, IntentionAction> markInvoked) {
      myAction = action;
      myMarkInvoked = markInvoked;
    }

    @Nls
    @NotNull
    @Override
    public String getText() {
      return myAction.getText();
    }

    @Override
    public String toString() {
      return getDelegate().getClass()+": "+getDelegate();
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return myAction.getFamilyName();
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
      return myAction.isAvailable(project, editor, file);
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
      myAction.invoke(project, editor, file);
      if (myMarkInvoked != null) {
        myMarkInvoked.accept(IntentionActionWithTextCaching.this, myAction);
      }
    }

    @Override
    public boolean startInWriteAction() {
      return myAction.startInWriteAction();
    }

    @NotNull
    @Override
    public IntentionAction getDelegate() {
      return myAction;
    }

    @Nullable
    @Override
    public PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
      return myAction.getElementToMakeWritable(currentFile);
    }

    @Nullable
    @Override
    public ShortcutSet getShortcut() {
      return myAction instanceof ShortcutProvider ? ((ShortcutProvider)myAction).getShortcut() : null;
    }

    @Override
    public int compareTo(@NotNull final MyIntentionAction other) {
      if (myAction instanceof Comparable) {
        //noinspection unchecked
        return ((Comparable)myAction).compareTo(other.getDelegate());
      }
      if (other.getDelegate() instanceof Comparable) {
        //noinspection unchecked
        return -((Comparable)other.getDelegate()).compareTo(myAction);
      }
      return Comparing.compare(getText(), other.getText());
    }
  }
}
