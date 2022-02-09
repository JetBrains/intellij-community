// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethodObject;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

final class ExtractGeneratedClassUtil {
  private static final String GENERATED_CLASS_PACKAGE = "idea.debugger.rt";
  private static final Logger LOG = Logger.getInstance(ExtractGeneratedClassUtil.class);

  static PsiClass extractGeneratedClass(@NotNull PsiClass generatedInnerClass,
                                        @NotNull PsiElementFactory elementFactory,
                                        @NotNull PsiElement anchor) {
    Project project = generatedInnerClass.getProject();

    PsiClass extractedClass = elementFactory.createClass("GeneratedEvaluationClass");

    for (PsiField field : generatedInnerClass.getAllFields()) {
      extractedClass.add(elementFactory.createFieldFromText(field.getText(), anchor)); // TODO: check if null is OK
    }

    for (PsiMethod psiMethod : generatedInnerClass.getMethods()) {
      extractedClass.add(elementFactory.createMethodFromText(psiMethod.getText(), anchor));
    }

    PsiJavaFile generatedFile = (PsiJavaFile)PsiFileFactory.getInstance(project)
      .createFileFromText(extractedClass.getName() + ".java", JavaFileType.INSTANCE, extractedClass.getContainingFile().getText());
    // copy.getModificationStamp(),
    //false, false);
    generatedFile.setPackageName(GENERATED_CLASS_PACKAGE);
    extractedClass = PsiTreeUtil.findChildOfType(generatedFile, PsiClass.class);
    copyStaticImports(generatedInnerClass, generatedFile, elementFactory);
    assert extractedClass != null;
    PsiElement codeBlock = PsiTreeUtil.findFirstParent(anchor, false, element -> element instanceof PsiCodeBlock);
    if (codeBlock == null) {
      codeBlock = anchor.getParent();
    }

    addGeneratedClassInfo(codeBlock, generatedInnerClass, extractedClass);
    return extractedClass;
  }

  private static void copyStaticImports(@NotNull PsiElement from,
                                        @NotNull PsiJavaFile destFile,
                                        @NotNull PsiElementFactory elementFactory) {
    PsiJavaFile fromFile = PsiTreeUtil.getParentOfType(from, PsiJavaFile.class);
    if (fromFile != null) {
      PsiImportList sourceImportList = fromFile.getImportList();
      if (sourceImportList != null) {
        PsiImportList destImportList = destFile.getImportList();
        LOG.assertTrue(destImportList != null, "import list of destination file should not be null");
        for (PsiImportStatementBase importStatement : sourceImportList.getAllImportStatements()) {
          if (importStatement instanceof PsiImportStaticStatement && isPublic((PsiImportStaticStatement)importStatement)) {
            PsiElement importStatementCopy = copy((PsiImportStaticStatement)importStatement, elementFactory);
            if (importStatementCopy != null) {
              destImportList.add(importStatementCopy);
            }
            else {
              LOG.warn("Unable to copy static import statement: " + importStatement.getText());
            }
          }
        }
      }
    }
  }

  @Nullable
  private static PsiElement copy(@NotNull PsiImportStaticStatement importStatement,
                                 @NotNull PsiElementFactory elementFactory) {
    PsiClass targetClass = importStatement.resolveTargetClass();
    String memberName = importStatement.getReferenceName();
    if (targetClass != null && memberName != null) {
      return elementFactory.createImportStaticStatement(targetClass, memberName);
    }

    return null;
  }

  private static boolean isPublic(@NotNull PsiImportStaticStatement staticImport) {
    PsiClass targetClass = staticImport.resolveTargetClass();
    if (targetClass != null && isPublicClass(targetClass)) {
      PsiElement importedElement = staticImport.resolve();
      if (importedElement instanceof PsiModifierListOwner) {
        return ((PsiModifierListOwner)importedElement).hasModifierProperty(PsiModifier.PUBLIC);
      }
    }
    return false;
  }

  private static boolean isPublicClass(@NotNull PsiClass psiClass) {
    while (psiClass != null) {
      if (!psiClass.hasModifierProperty(PsiModifier.PUBLIC)) {
        return false;
      }
      psiClass = psiClass.getContainingClass();
    }

    return true;
  }

  private static void addGeneratedClassInfo(@NotNull PsiElement element,
                                            @NotNull PsiClass generatedClass,
                                            @NotNull PsiClass extractedClass) {
    generatedClass.putUserData(LightMethodObjectExtractedData.REFERENCED_TYPE, PsiTypesUtil.getClassType(extractedClass));
    element.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitNewExpression(PsiNewExpression expression) {
        super.visitNewExpression(expression);
        PsiMethod constructor = expression.resolveConstructor();
        if (constructor != null && generatedClass.equals(constructor.getContainingClass())) {
          List<PsiMethod> methods =
            Arrays.stream(extractedClass.getConstructors()).filter(x -> isSameMethod(x, constructor)).collect(Collectors.toList());
          if (methods.size() == 1) {
            LOG.info("Replace constructor: " + constructor.getName());
            constructor.putUserData(LightMethodObjectExtractedData.REFERENCE_METHOD, methods.get(0));
          }
        }
      }

      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        super.visitMethodCallExpression(expression);
        PsiMethod method = expression.resolveMethod();
        if (method != null && generatedClass.equals(method.getContainingClass())) {
          List<PsiMethod> methods =
            Arrays.stream(extractedClass.getMethods()).filter(x -> isSameMethod(x, method)).collect(Collectors.toList());
          if (methods.size() == 1) {
            LOG.info("Replace method: " + method.getName());
            method.putUserData(LightMethodObjectExtractedData.REFERENCE_METHOD, methods.get(0));
          }
        }
      }

      private boolean isSameMethod(@NotNull PsiMethod first, @NotNull PsiMethod second) {
        if (first.getName().equals(second.getName())) {
          return first.getParameterList().getParametersCount() == second.getParameterList().getParametersCount();
        }
        return false;
      }
    });
  }
}
