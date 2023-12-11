// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.java.JavaBundle;
import com.intellij.modcommand.*;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

import static com.intellij.psi.util.ImportsUtil.*;

public final class ExpandStaticImportAction extends PsiBasedModCommandAction<PsiIdentifier> {
  private final @NotNull ThreeState myExpandAll;
  
  public ExpandStaticImportAction() {
    super(PsiIdentifier.class);
    myExpandAll = ThreeState.UNSURE;
  }

  private ExpandStaticImportAction(boolean expandAll) {
    super(PsiIdentifier.class);
    myExpandAll = ThreeState.fromBoolean(expandAll);
  }
  
  @Override
  @NotNull
  public String getFamilyName() {
    return JavaBundle.message("intention.family.expand.static.import");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiIdentifier element) {
    if (!PsiUtil.isLanguageLevel5OrHigher(element) || !(element.getParent() instanceof PsiJavaCodeReferenceElement referenceElement)) {
      return null;
    }
    final PsiImportStaticStatement importStatement = getImportStaticStatement(referenceElement);
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

  private static PsiImportStaticStatement getImportStaticStatement(PsiJavaCodeReferenceElement referenceElement) {
    PsiElement parent = referenceElement.getParent();
    return parent instanceof PsiImportStaticStatement importStatic ? importStatic :
           ObjectUtils.tryCast(referenceElement.advancedResolve(true).getCurrentFileResolveScope(), PsiImportStaticStatement.class);
  }

  @Override
  protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiIdentifier element) {
    final PsiJavaCodeReferenceElement refExpr = (PsiJavaCodeReferenceElement)element.getParent();
    ThreeState expandAll = myExpandAll == ThreeState.UNSURE && refExpr.getParent() instanceof PsiImportStaticStatement ? ThreeState.YES :
                           myExpandAll;

    return switch (expandAll) {
      case YES -> ModCommand.psiUpdate(refExpr, refExprCopy -> {
        PsiImportStaticStatement staticImport = getImportStaticStatement(refExprCopy);
        List<PsiJavaCodeReferenceElement> expressionToExpand = collectReferencesThrough(
          refExprCopy.getContainingFile(), refExprCopy, staticImport);
        replaceAllAndDeleteImport(expressionToExpand, refExprCopy, staticImport);
      });
      case NO -> ModCommand.psiUpdate(refExpr, refExprCopy -> expand(refExprCopy, getImportStaticStatement(refExprCopy)));
      case UNSURE -> {
        final PsiImportStaticStatement staticImport = Objects.requireNonNull(getImportStaticStatement(refExpr));
        List<PsiJavaCodeReferenceElement> expressionToExpand = collectReferencesThrough(context.file(), refExpr, staticImport);

        if (expressionToExpand.isEmpty()) {
          yield ModCommand.psiUpdate(context, updater -> {
            PsiImportStaticStatement staticImportCopy = updater.getWritable(staticImport);
            PsiJavaCodeReferenceElement refExprCopy = updater.getWritable(refExpr);
            expand(refExprCopy, staticImportCopy);
            staticImportCopy.delete();
          });
        }
        yield ModCommand.chooseAction(JavaBundle.message("multiple.usages.of.static.import.found"),
                                      new ExpandStaticImportAction(false), new ExpandStaticImportAction(true));
      }
    };
  }
}
