// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.intention.CustomizableIntentionAction;
import com.intellij.codeInsight.intention.CustomizableIntentionActionDelegate;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.actionSystem.ShortcutProvider;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SlowOperations;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public class IntentionActionWithTextCaching implements Comparable<IntentionActionWithTextCaching>, PossiblyDumbAware, ShortcutProvider, IntentionActionDelegate {
  private static final Logger LOG = Logger.getInstance(IntentionActionWithTextCaching.class);
  private final List<IntentionAction> myOptionIntentions = new ArrayList<>();
  private final List<IntentionAction> myOptionErrorFixes = new ArrayList<>();
  private final List<IntentionAction> myOptionInspectionFixes = new ArrayList<>();
  private final @IntentionName String myText;
  private final IntentionAction myAction;
  private final @NlsContexts.PopupTitle String myDisplayName;
  private final Icon myIcon;

  IntentionActionWithTextCaching(@NotNull IntentionAction action,
                                 @NlsContexts.PopupTitle String displayName,
                                 @Nullable Icon icon,
                                 @NotNull BiConsumer<? super IntentionActionWithTextCaching, ? super IntentionAction> markInvoked) {
    myIcon = icon;
    myText = action.getText();
    // needed for checking errors in user written actions
    LOG.assertTrue(myText != null, "action " + action.getClass() + " text returned null");
    myAction = new MyIntentionAction(action, markInvoked);
    myDisplayName = displayName;
  }

  public @NotNull @IntentionName String getText() {
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

  public @NotNull IntentionAction getAction() {
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

  public @NotNull List<IntentionAction> getOptionActions() {
    return ContainerUtil.concat(myOptionIntentions, myOptionErrorFixes, myOptionInspectionFixes);
  }

  @NlsContexts.PopupTitle String getToolName() {
    return myDisplayName;
  }

  @Override
  public @NotNull String toString() {
    return getText();
  }

  @Override
  public int compareTo(@NotNull IntentionActionWithTextCaching other) {
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

  @Override
  public @Nullable ShortcutSet getShortcut() {
    ShortcutSet shortcut = myAction instanceof ShortcutProvider ? ((ShortcutProvider)myAction).getShortcut() : null;
    return shortcut != null ? shortcut : IntentionShortcutManager.getInstance().getShortcutSet(myAction);
  }

  @Override
  public @NotNull IntentionAction getDelegate() {
    return getAction();
  }

  boolean isShowSubmenu() {
    IntentionAction action = IntentionActionDelegate.unwrap(getDelegate());
    if (action instanceof CustomizableIntentionAction) {
      return ((CustomizableIntentionAction)myAction).isShowSubmenu();
    }
    return true;
  }

  public boolean isSelectable() {
    IntentionAction action = IntentionActionDelegate.unwrap(getDelegate());
    if (action instanceof CustomizableIntentionAction) {
      return ((CustomizableIntentionAction)myAction).isSelectable();
    }
    return true;
  }

  public boolean isShowIcon() {
    IntentionAction action = IntentionActionDelegate.unwrap(getDelegate());
    if (action instanceof CustomizableIntentionAction) {
      return ((CustomizableIntentionAction)action).isShowIcon();
    }
    return true;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof IntentionActionWithTextCaching other)) return false;
    return getActionClass(this) == getActionClass(other) && this.getText().equals(other.getText());
  }

  private static Class<? extends IntentionAction> getActionClass(IntentionActionWithTextCaching o1) {
    return IntentionActionDelegate.unwrap(o1.getAction()).getClass();
  }

  @Override
  public int hashCode() {
    return getText().hashCode();
  }

  // IntentionAction which wraps the original action and then marks it as executed to hide it from the popup to avoid invoking it twice accidentally
  private class MyIntentionAction implements IntentionAction, CustomizableIntentionActionDelegate, Comparable<MyIntentionAction>,
                                             ShortcutProvider, PossiblyDumbAware {
    private final IntentionAction myAction;
    private final @NotNull BiConsumer<? super IntentionActionWithTextCaching, ? super IntentionAction> myMarkInvoked;

    MyIntentionAction(@NotNull IntentionAction action, @NotNull BiConsumer<? super IntentionActionWithTextCaching, ? super IntentionAction> markInvoked) {
      myAction = action;
      myMarkInvoked = markInvoked;
    }

    @Override
    public boolean isDumbAware() {
      return DumbService.isDumbAware(myAction);
    }

    @Override
    public @NotNull String getText() {
      return myText;
    }

    @Override
    public String toString() {
      return getDelegate()+" ("+getDelegate().getClass()+")";
    }

    @Override
    public @NotNull @Nls String getFamilyName() {
      return myAction.getFamilyName();
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
      return myAction.isAvailable(project, editor, file);
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
      SlowOperations.allowSlowOperations(() -> myAction.invoke(project, editor, file));
      myMarkInvoked.accept(IntentionActionWithTextCaching.this, myAction);
    }

    @Override
    public boolean startInWriteAction() {
      return myAction.startInWriteAction();
    }

    @Override
    public @NotNull IntentionAction getDelegate() {
      return myAction;
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
      return myAction.generatePreview(project, editor, file);
    }

    @Override
    public @Nullable PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
      return myAction.getElementToMakeWritable(currentFile);
    }

    @Override
    public @Nullable ShortcutSet getShortcut() {
      return myAction instanceof ShortcutProvider
             ? ((ShortcutProvider)myAction).getShortcut()
             : IntentionShortcutManager.getInstance().getShortcutSet(myAction);
    }

    @Override
    public int compareTo(@NotNull MyIntentionAction other) {
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
