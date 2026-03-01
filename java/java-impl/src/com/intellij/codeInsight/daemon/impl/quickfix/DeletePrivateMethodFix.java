// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiBasedModCommandAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DeletePrivateMethodFix extends PsiBasedModCommandAction<PsiMethod> {
  private final ThreeState myDeleteCalled;
  
  public DeletePrivateMethodFix() {
    super(PsiMethod.class);
    myDeleteCalled = ThreeState.UNSURE;
  }

  public DeletePrivateMethodFix(@NotNull PsiMethod method) {
    this(method, ThreeState.UNSURE);
  }

  private DeletePrivateMethodFix(@NotNull PsiMethod method, ThreeState deleteCalled) {
    super(method);
    myDeleteCalled = deleteCalled;
  }

  @Override
  protected @NotNull Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiMethod method) {
    return switch (myDeleteCalled) {
      case UNSURE -> Presentation.of(JavaBundle.message("intention.name.delete.method", method.getName()));
      case YES -> Presentation.of(JavaBundle.message("intention.name.delete.method.with.callees"));
      case NO -> Presentation.of(JavaBundle.message("intention.name.delete.method.only"));
    };
  }

  @Override
  protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiMethod method) {
    if (!method.hasModifierProperty(PsiModifier.PRIVATE)) return ModCommand.nop();
    List<PsiElement> elements;
    if (myDeleteCalled == ThreeState.NO) {
      elements = List.of();
    }
    else {
      Set<PsiElement> finalElements = new HashSet<>();
      Deque<PsiMethod> toProcess = new ArrayDeque<>();
      toProcess.add(method);
      finalElements.add(method);
      while (!toProcess.isEmpty()) {
        PsiMethod next = toProcess.poll();
        List<PsiMethod> newMethods = ContainerUtil.filterIsInstance(SafeDeleteFix.computeReferencedCodeSafeToDelete(
          next, t -> t instanceof PsiMethod m && m.hasModifierProperty(PsiModifier.PRIVATE)), PsiMethod.class);
        for (PsiMethod newMethod : newMethods) {
          if (finalElements.add(newMethod)) {
            toProcess.add(newMethod);
          }
        }
      }
      elements = List.copyOf(finalElements);
    }
    if (elements.isEmpty()) {
      return ModCommand.psiUpdate(method, m -> m.delete());
    }
    if (myDeleteCalled == ThreeState.UNSURE) {
      return ModCommand.chooseAction(JavaBundle.message("intention.name.delete.method.title", method.getName()),
                                     new DeletePrivateMethodFix(method, ThreeState.YES),
                                     new DeletePrivateMethodFix(method, ThreeState.NO));
    }
    return ModCommand.psiUpdate(method, (m, updater) -> {
      List<PsiElement> writable = ContainerUtil.map(elements, updater::getWritable);
      writable.forEach(PsiElement::delete);
      m.delete();
    });
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaBundle.message("intention.family.name.delete.private.method");
  }
}
