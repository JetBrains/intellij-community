// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiBasedModCommandAction;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiImportStaticStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

import static com.intellij.psi.util.ImportsUtil.collectReferencesThrough;
import static com.intellij.psi.util.ImportsUtil.expand;
import static com.intellij.psi.util.ImportsUtil.replaceAllAndDeleteImport;

public final class ExpandStaticImportAction extends PsiBasedModCommandAction<PsiElement> {
  private final @NotNull ThreeState myExpandAll;

  public ExpandStaticImportAction() {
    super(PsiElement.class);
    myExpandAll = ThreeState.UNSURE;
  }

  private ExpandStaticImportAction(boolean expandAll) {
    super(PsiElement.class);
    myExpandAll = ThreeState.fromBoolean(expandAll);
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaBundle.message("intention.family.expand.static.import");
  }

  @Override
  protected boolean isElementApplicable(@NotNull PsiElement element, @NotNull ActionContext context) {
    return getImportStaticStatement(element) != null;
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiElement element) {
    if (!PsiUtil.isAvailable(JavaFeature.STATIC_IMPORTS, element)) {
      return null;
    }
    final PsiImportStaticStatement importStatement = getImportStaticStatement(element);
    if (importStatement == null) return null;
    final PsiClass targetClass = importStatement.resolveTargetClass();
    if (targetClass == null) return null;
    String message = switch (myExpandAll) {
      case YES -> JavaBundle.message("intention.text.replace.all.delete.import");
      case NO -> JavaBundle.message("intention.text.replace.this.occurrence.keep.import");
      case UNSURE -> JavaBundle.message("intention.text.replace.static.import.with.qualified.access.to.0", targetClass.getName());
    };
    return Presentation.of(message);
  }

  private static PsiImportStaticStatement getImportStaticStatement(PsiElement element) {
    if (element instanceof PsiImportStaticStatement st) return st;
    if (element.getParent() instanceof PsiImportStaticStatement st) return st;
    return element instanceof PsiJavaCodeReferenceElement ref
           && ref.advancedResolve(true).getCurrentFileResolveScope() instanceof PsiImportStaticStatement st ? st : null;
  }

  @Override
  protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiElement element) {
    PsiImportStaticStatement statement = PsiTreeUtil.getParentOfType(element, PsiImportStaticStatement.class);
    ThreeState expandAll = myExpandAll == ThreeState.UNSURE && statement != null ? ThreeState.YES : myExpandAll;
    final PsiJavaCodeReferenceElement refExpr =
      statement != null ? Objects.requireNonNull(statement.getImportReference()) : (PsiJavaCodeReferenceElement)element;

    return switch (expandAll) {
      case YES -> ModCommand.psiUpdate(refExpr, refExprCopy -> {
        PsiImportStaticStatement staticImport = getImportStaticStatement(refExprCopy);
        List<PsiJavaCodeReferenceElement> expressionToExpand = 
          collectReferencesThrough(refExprCopy.getContainingFile(), refExprCopy, staticImport);
        replaceAllAndDeleteImport(expressionToExpand, refExprCopy, staticImport);
      });
      case NO -> ModCommand.psiUpdate(refExpr, refExprCopy -> {
        PsiClass aClass = Objects.requireNonNull(getImportStaticStatement(refExprCopy)).resolveTargetClass();
        if (aClass == null) return;
        expand(refExprCopy, aClass);
      });
      case UNSURE -> {
        assert !(element instanceof PsiImportStaticStatement);
        final PsiImportStaticStatement staticImport = Objects.requireNonNull(getImportStaticStatement(element));
        List<PsiJavaCodeReferenceElement> expressionToExpand = collectReferencesThrough(context.file(), refExpr, staticImport);

        if (expressionToExpand.isEmpty()) {
          yield ModCommand.psiUpdate(context, updater -> {
            PsiImportStaticStatement staticImportCopy = updater.getWritable(staticImport);
            PsiJavaCodeReferenceElement refExprCopy = updater.getWritable(refExpr);
            PsiClass aClass = staticImportCopy.resolveTargetClass();
            if (aClass == null) return;
            staticImportCopy.delete();
            expand(refExprCopy, aClass);
          });
        }
        yield ModCommand.chooseAction(JavaBundle.message("multiple.usages.of.static.import.found"),
                                      new ExpandStaticImportAction(false), new ExpandStaticImportAction(true));
      }
    };
  }
}
