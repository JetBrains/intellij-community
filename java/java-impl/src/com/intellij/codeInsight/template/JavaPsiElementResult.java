// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template;

import com.intellij.codeInsight.template.impl.JavaTemplateUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiVariable;


public class JavaPsiElementResult extends PsiElementResult {
  public JavaPsiElementResult(PsiElement element) {
    super(element);
  }

  @Override
  public String toString() {
    PsiElement element = getElement();
    return switch (element) {
      case PsiVariable variable -> variable.getName();
      case PsiMethod method -> method.getName() + "()";
      case PsiClass aClass -> {
        PsiIdentifier identifier = aClass.getNameIdentifier();
        yield identifier == null ? "" : identifier.getText();
      }
      case null, default -> super.toString();
    };
  }

  @Override
  public void handleFocused(final PsiFile psiFile, final Document document, final int segmentStart, final int segmentEnd) {
    JavaTemplateUtil.updateTypeBindings(getElement(), psiFile, document, segmentStart, segmentEnd);
  }
}
