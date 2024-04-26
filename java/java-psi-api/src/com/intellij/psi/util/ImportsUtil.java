// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ImportsUtil {
  private static final Key<Boolean> IS_IMPLICIT = Key.create("IMPORT_IS_IMPLICIT");

  private ImportsUtil() {
  }

  public static List<PsiJavaCodeReferenceElement> collectReferencesThrough(PsiFile file,
                                                                           @Nullable final PsiJavaCodeReferenceElement refExpr,
                                                                           final PsiImportStaticStatement staticImport) {
    final List<PsiJavaCodeReferenceElement> expressionToExpand = new ArrayList<>();
    file.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement expression) {
        if (refExpr != expression) {
          final PsiElement resolveScope = expression.advancedResolve(true).getCurrentFileResolveScope();
          if (resolveScope == staticImport) {
            expressionToExpand.add(expression);
          }
        }
        super.visitElement(expression);
      }
    });
    return expressionToExpand;
  }

  public static void replaceAllAndDeleteImport(List<PsiJavaCodeReferenceElement> expressionToExpand,
                                               @Nullable PsiJavaCodeReferenceElement refExpr,
                                                PsiImportStaticStatement staticImport) {
    if (refExpr != null) {
      expressionToExpand.add(refExpr);
    }
    expressionToExpand.sort((o1, o2) -> o2.getTextOffset() - o1.getTextOffset());
    for (PsiJavaCodeReferenceElement expression : expressionToExpand) {
      expand(expression, staticImport);
    }
    staticImport.delete();
  }

  public static void expand(@NotNull PsiJavaCodeReferenceElement ref, PsiImportStaticStatement staticImport) {
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(ref.getProject());
    PsiClass targetClass = staticImport.resolveTargetClass();
    assert targetClass != null;
    if (ref instanceof PsiReferenceExpression) {
      ((PsiReferenceExpression)ref).setQualifierExpression(elementFactory.createReferenceExpression(targetClass));
    }
    else if (ref instanceof PsiImportStaticReferenceElement) {
      ref.replace(
        Objects.requireNonNull(elementFactory.createImportStaticStatement(targetClass, ref.getText()).getImportReference()));
    }
    else {
      ref.replace(elementFactory.createReferenceFromText(targetClass.getQualifiedName() + "." + ref.getText(), ref));
    }
  }

  public static boolean hasStaticImportOn(final PsiElement expr, final PsiMember member, boolean acceptOnDemand) {
    if (expr.getContainingFile() instanceof PsiJavaFile) {
      final PsiImportList importList = ((PsiJavaFile)expr.getContainingFile()).getImportList();
      if (importList != null) {
        final PsiImportStaticStatement[] importStaticStatements = importList.getImportStaticStatements();
        for (PsiImportStaticStatement stmt : importStaticStatements) {
          final PsiClass containingClass = member.getContainingClass();
          final String referenceName = stmt.getReferenceName();
          if (containingClass != null && stmt.resolveTargetClass() == containingClass) {
            if (!stmt.isOnDemand() && Comparing.strEqual(referenceName, member.getName())) {
              if (member instanceof PsiMethod) {
                return containingClass.findMethodsByName(referenceName, false).length > 0;
              }
              return true;
            }
            if (acceptOnDemand && stmt.isOnDemand()) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  public static void markAsImplicitImport(@NotNull PsiImportStatementBase importStatement) {
    importStatement.putUserData(IS_IMPLICIT, Boolean.TRUE);
  }

  public static boolean isImplicitImport(@NotNull PsiImportStatementBase importStatement) {
    return importStatement.getUserData(IS_IMPLICIT) == Boolean.TRUE;
  }
}
