// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.openapi.util.Comparing;
import com.intellij.psi.ImplicitlyImportedElement;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiImportStaticReferenceElement;
import com.intellij.psi.PsiImportStaticStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ImportsUtil {

  private ImportsUtil() {}

  public static List<PsiJavaCodeReferenceElement> collectReferencesThrough(PsiFile file,
                                                                           @Nullable PsiJavaCodeReferenceElement refExpr,
                                                                           PsiImportStaticStatement staticImport) {
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

  public static void replaceAllAndDeleteImport(List<PsiJavaCodeReferenceElement> expressionsToExpand,
                                               @Nullable PsiJavaCodeReferenceElement refExpr,
                                               PsiImportStaticStatement staticImport) {
    if (refExpr != null) {
      expressionsToExpand.add(refExpr);
    }

    expressionsToExpand.sort((o1, o2) -> o2.getTextOffset() - o1.getTextOffset());

    PsiClass targetClass = staticImport.resolveTargetClass();
    assert targetClass != null;

    PsiJavaFile file = (PsiJavaFile)staticImport.getContainingFile();
    protectTrailingComment(file, () -> {
      for (PsiJavaCodeReferenceElement expression : ContainerUtil.filter(expressionsToExpand, e -> !(e.getParent() instanceof PsiAnnotation))) {
        if (PsiTreeUtil.isAncestor(staticImport, expression, false)) continue;
        expand(expression, targetClass);
      }
      staticImport.delete();
      for (PsiJavaCodeReferenceElement expression : ContainerUtil.filter(expressionsToExpand, e -> e.getParent() instanceof PsiAnnotation)) {
        expand(expression, targetClass);
      }
    });
  }

  /**
   * Runs {@code action}, which is expected to delete and/or re-add an import (see {@link #expand}), with
   * the comment directly following the import list protected: re-adding an import can otherwise glue it
   * onto that comment with no separating whitespace, which then misparses as part of the import list on
   * the next reparse.
   */
  public static void protectTrailingComment(@NotNull PsiJavaFile file, @NotNull Runnable action) {
    final List<PsiElement> trailingComment = detachTrailingComment(file);
    action.run();
    restoreTrailingComment(file, trailingComment);
  }

  private static @NotNull List<PsiElement> detachTrailingComment(@NotNull PsiJavaFile file) {
    final PsiImportList importList = file.getImportList();
    if (importList == null) {
      return Collections.emptyList();
    }
    final List<PsiElement> elements = new ArrayList<>();
    boolean hasComment = false;
    for (PsiElement e = importList.getNextSibling(); e instanceof PsiWhiteSpace || e instanceof PsiComment; e = e.getNextSibling()) {
      hasComment |= e instanceof PsiComment;
      elements.add(e);
    }
    if (!hasComment) {
      return Collections.emptyList();
    }
    final List<PsiElement> copies = ContainerUtil.map(elements, PsiElement::copy);
    importList.getParent().deleteChildRange(elements.get(0), elements.get(elements.size() - 1));
    return copies;
  }

  private static void restoreTrailingComment(@NotNull PsiJavaFile file, @NotNull List<PsiElement> elements) {
    if (elements.isEmpty()) {
      return;
    }
    PsiElement anchor = file.getImportList();
    if (anchor == null) {
      anchor = file.getPackageStatement();
    }
    if (anchor == null) {
      anchor = file.getFirstChild();
    }
    if (anchor == null) {
      return;
    }
    final PsiElement parent = anchor.getParent();
    for (PsiElement element : elements) {
      anchor = parent.addAfter(element, anchor);
    }
  }

  public static void expand(@NotNull PsiJavaCodeReferenceElement ref, @NotNull PsiClass targetClass) {
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(ref.getProject());
    if (ref instanceof PsiReferenceExpression) {
      ((PsiReferenceExpression)ref).setQualifierExpression(elementFactory.createReferenceExpression(targetClass));
    }
    else if (ref instanceof PsiImportStaticReferenceElement) {
      ref.replace(Objects.requireNonNull(elementFactory.createImportStaticStatement(targetClass, ref.getText()).getImportReference()));
    }
    else {
      PsiElement replaced = ref.replace(elementFactory.createReferenceFromText(targetClass.getQualifiedName() + "." + ref.getText(), ref));
      JavaCodeStyleManager.getInstance(ref.getProject()).shortenClassReferences(replaced);
    }
  }

  public static boolean hasStaticImportOn(PsiElement expr, PsiMember member, boolean acceptOnDemand) {
    if (expr.getContainingFile() instanceof PsiJavaFile) {
      PsiJavaFile file = (PsiJavaFile)expr.getContainingFile();
      final PsiImportList importList = file.getImportList();
      if (importList != null) {
        List<PsiImportStaticStatement> additionalOnDemandImports =
          ContainerUtil.filterIsInstance(getAllImplicitImports(file), PsiImportStaticStatement.class);
        final PsiImportStaticStatement[] importStaticStatements = importList.getImportStaticStatements();
        for (PsiImportStaticStatement stmt : ContainerUtil.append(additionalOnDemandImports, importStaticStatements)) {
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

  /**
   * Retrieves all implicit import statements associated with the given Java file.
   *
   * @param file the Java file for which to retrieve implicit import statements.
   * @return a list of implicit import statements associated with the given Java file.
   */
  public static @Unmodifiable List<PsiImportStatementBase> getAllImplicitImports(@NotNull PsiJavaFile file) {
    return CachedValuesManager.getProjectPsiDependentCache(file, javaFile -> {
      List<PsiImportStatementBase> results = new ArrayList<>();
      for (ImplicitlyImportedElement element : javaFile.getImplicitlyImportedElements()) {
        results.add(element.createImportStatement());
      }
      PsiElementFactory factory = PsiElementFactory.getInstance(javaFile.getProject());
      for (String aPackage : javaFile.getImplicitlyImportedPackages()) {
        results.add(factory.createImportStatementOnDemand(aPackage));
      }
      return Collections.unmodifiableList(results);
    });
  }
}
