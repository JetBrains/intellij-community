// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethodObject;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

class ExtractGeneratedClassUtil {
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
    assert extractedClass != null;
    PsiElement codeBlock = PsiTreeUtil.findFirstParent(anchor, false, element -> element instanceof PsiCodeBlock);
    if (codeBlock == null) {
      codeBlock = anchor.getParent();
    }

    addGeneratedClassInfo(codeBlock, generatedInnerClass, extractedClass);
    return extractedClass;
  }

  private static void addGeneratedClassInfo(@NotNull PsiElement element,
                                            @NotNull PsiClass generatedClass,
                                            @NotNull PsiClass extractedClass) {
    generatedClass.putUserData(ExtractLightMethodObjectHandler.REFERENCED_TYPE, PsiTypesUtil.getClassType(extractedClass));
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
            constructor.putUserData(ExtractLightMethodObjectHandler.REFERENCE_METHOD, methods.get(0));
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
            method.putUserData(ExtractLightMethodObjectHandler.REFERENCE_METHOD, methods.get(0));
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
