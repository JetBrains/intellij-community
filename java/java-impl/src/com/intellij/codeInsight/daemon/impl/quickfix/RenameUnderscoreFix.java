// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiBasedModCommandAction;
import com.intellij.openapi.util.Key;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.ResolveState;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.util.JavaElementKind;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.VariableNameGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class RenameUnderscoreFix extends PsiBasedModCommandAction<PsiReferenceExpression> {
  public RenameUnderscoreFix(@NotNull PsiReferenceExpression element) {
    super(element);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiReferenceExpression element) {
    if (findDeclarations(element).isEmpty()) return null;
    return super.getPresentation(context, element);
  }

  @Override
  protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiReferenceExpression element) {
    List<PsiVariable> declarations = findDeclarations(element);
    List<@NotNull ModCommandAction> actions = ContainerUtil.map(declarations, var -> ModCommand.psiUpdateStep(
      var, JavaBundle.message("intention.rename.underscore.name", JavaElementKind.fromElement(var).subject(), var.getType().getPresentableText()), (v, updater) -> {
        if (!element.isValid()) return;
        PsiReferenceExpression writableRef = updater.getWritable(element);
        List<String> names = new VariableNameGenerator(v, VariableKind.LOCAL_VARIABLE)
          .byType(v.getType())
          .generateAll(true);
        String defaultName = names.getFirst();
        v.setName(defaultName);
        writableRef.replace(JavaPsiFacade.getElementFactory(context.project()).createExpressionFromText(defaultName, writableRef));
        updater.rename(v, names);
      }, v -> {
        PsiIdentifier identifier = v.getNameIdentifier();
        PsiTypeElement typeElement = v.getTypeElement();
        if (identifier == null) return v.getTextRange();
        if (typeElement == null) return identifier.getTextRange();
        return typeElement.getTextRange().union(identifier.getTextRange());
      }));
    return ModCommand.chooseAction(JavaBundle.message("intention.rename.underscore.popup.title"), actions);
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaBundle.message("intention.rename.underscore.family.name");
  }

  public static List<PsiVariable> findDeclarations(@NotNull PsiReferenceExpression expression) {
    List<PsiVariable> result = new ArrayList<>();
    PsiScopesUtil.treeWalkUp(new PsiScopeProcessor() {
      @SuppressWarnings("unchecked")
      @Override
      public <T> @Nullable T getHint(@NotNull Key<T> hintKey) {
        if (hintKey == ElementClassHint.KEY) {
          return (T)(ElementClassHint)(ElementClassHint.DeclarationKind.VARIABLE::equals);
        }
        if (hintKey == ElementClassHint.PROCESS_UNNAMED_VARIABLES) {
          return (T)Boolean.TRUE;
        }
        return null;
      }

      @Override
      public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
        if (element instanceof PsiVariable variable && PsiUtil.isJvmLocalVariable(variable) && variable.isUnnamed()) {
          result.add(variable);
        }
        return true;
      }
    }, expression, expression.getContainingFile());
    return result;
  }
}
