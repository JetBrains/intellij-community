// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethodObject;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

class ExtractGeneratedClassUtil {
  private static final String GENERATED_CLASS_PACKAGE = "idea.debugger.rt";

  static PsiClass extractGeneratedClass(@NotNull PsiMethodCallExpression generatedMethodCall,
                                        @NotNull PsiClass generatedInnerClass,
                                        @NotNull PsiElementFactory elementFactory,
                                        @NotNull PsiElement anchor) {
    Project project = generatedInnerClass.getProject();
    ReflectionAccessorToEverything.grantAccessThroughReflection(generatedInnerClass, generatedMethodCall, elementFactory);

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

    return extractedClass;
  }
}
