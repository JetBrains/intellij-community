// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.intention.AbstractIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInsight.intention.impl.IntentionActionWithTextCaching;
import com.intellij.codeInsight.intention.impl.PriorityIntentionActionWrapper;
import com.intellij.codeInspection.IntentionWrapper;
import com.intellij.lang.impl.modcommand.ModCommandServiceImpl;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.PsiBasedModCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public class IntentionWrappersTest extends TestCase {
  public void testIntentionWrapperDelegatesDumbawareness() {
    checkDumbAwarenessIsDelegatedThrough(() -> new IntentionWrapper(new MyIntentionAction()));
  }

  private static void checkDumbAwarenessIsDelegatedThrough(Supplier<?> o) {
    if (!Registry.is("ide.dumb.mode.check.awareness")) return;
    MyIntentionAction.SWITCH = false;
    assertFalse(DumbService.isDumbAware(o.get()));
    MyIntentionAction.SWITCH = true;
    assertTrue(DumbService.isDumbAware(o.get()));
  }

  public void testIntentionActionDelegateDelegatesDumbawareness() {
    MyIntentionAction delegate = new MyIntentionAction();
    class MyDelegate implements IntentionActionDelegate {
      @Override
      public @NotNull IntentionAction getDelegate() {
        return delegate;
      }
    }
    checkDumbAwarenessIsDelegatedThrough(() -> new MyDelegate());
  }
  public void testIntentionActionWithTextCachingDelegatesDumbawareness() {
    checkDumbAwarenessIsDelegatedThrough(() -> new IntentionActionWithTextCaching(new MyIntentionAction()));
  }
  public void testPriorityIntentionActionWrapperDelegatesDumbawareness() {
    checkDumbAwarenessIsDelegatedThrough(() -> PriorityIntentionActionWrapper.highPriority(new MyIntentionAction()));
  }
  public void testModCommandActionWrapperDelegatesDumbawareness() {
    class MyModCommand extends PsiBasedModCommandAction implements PossiblyDumbAware {
      protected MyModCommand() {
        super(PsiElement.class);
      }

      @Override
      public boolean isDumbAware() {
        return MyIntentionAction.SWITCH;
      }

      @Override
      protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiElement element) {
        return null;
      }

      @Override
      public @NotNull String getFamilyName() {
        return "";
      }
    }
    checkDumbAwarenessIsDelegatedThrough(() -> new ModCommandServiceImpl().wrap(new MyModCommand()));
  }
}
class MyIntentionAction extends AbstractIntentionAction {
  static boolean SWITCH;
  @Override
  public @NotNull String getText() {
    return "";
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {

  }

  @Override
  public boolean isDumbAware() {
    return SWITCH;
  }
}
