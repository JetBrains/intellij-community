// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.ImplicitlyImportedElement;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationOwner;
import com.intellij.psi.PsiClass;
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
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ImportsUtil {

  private ImportsUtil() {
  }

  public static List<PsiJavaCodeReferenceElement> collectReferencesThrough(PsiFile file,
                                                                           final @Nullable PsiJavaCodeReferenceElement refExpr,
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

    PsiClass targetClass = staticImport.resolveTargetClass();
    assert targetClass != null;

    for (PsiJavaCodeReferenceElement expression : ContainerUtil.filter(expressionToExpand, e -> !(e.getParent() instanceof PsiAnnotation)) ) {
      if(PsiTreeUtil.isAncestor(staticImport, expression, false)) continue;
      expand(expression, targetClass);
    }

    staticImport.delete();

    for (PsiJavaCodeReferenceElement expression : ContainerUtil.filter(expressionToExpand, e -> (e.getParent() instanceof PsiAnnotation)) ) {
      expand(expression, targetClass);
    }
  }

  public static void expand(@NotNull PsiJavaCodeReferenceElement ref, @NotNull PsiClass targetClass) {
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(ref.getProject());
    if (ref instanceof PsiReferenceExpression) {
      ((PsiReferenceExpression)ref).setQualifierExpression(elementFactory.createReferenceExpression(targetClass));
    }
    else if (ref.getParent() instanceof PsiAnnotation) {
      PsiAnnotation oldAnnotation = (PsiAnnotation)ref.getParent();
      PsiAnnotationOwner owner = oldAnnotation.getOwner();
      if (owner != null) {
        PsiAnnotation annotation = owner.addAnnotation(targetClass.getQualifiedName() + "." + ref.getText());
        JavaCodeStyleManager.getInstance(ref.getProject()).shortenClassReferences(annotation);
        oldAnnotation.delete();
      }
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
    if (expr.getContainingFile() instanceof PsiJavaFile ) {
      PsiJavaFile file = (PsiJavaFile)expr.getContainingFile();
      final PsiImportList importList = file.getImportList();
      if (importList != null) {
        List<PsiImportStaticStatement> additionalOnDemandImports = ContainerUtil.filterIsInstance(getAllImplicitImports(file), PsiImportStaticStatement.class);
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
  public static List<PsiImportStatementBase> getAllImplicitImports(@NotNull PsiJavaFile file) {
    List<PsiImportStatementBase> cache = CachedValuesManager.getProjectPsiDependentCache(file, javaFile -> {
      List<PsiImportStatementBase> results = new ArrayList<>();
      Project project = javaFile.getProject();
      PsiElementFactory factory = PsiElementFactory.getInstance(project);
      ImplicitlyImportedElement[] elements = javaFile.getImplicitlyImportedElements();
      for (@NotNull ImplicitlyImportedElement element : elements) {
        results.add(element.createImportStatement());
      }
      for (String aPackage : javaFile.getImplicitlyImportedPackages()) {
        results.add(factory.createImportStatementOnDemand(aPackage));
      }
      return results;
    });
    return new ArrayList<>(cache);
  }
}
