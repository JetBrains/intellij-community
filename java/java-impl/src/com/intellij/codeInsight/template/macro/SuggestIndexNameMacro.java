// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.JavaCodeContextType;
import com.intellij.codeInsight.template.Macro;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.codeInsight.template.TextResult;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

public final class SuggestIndexNameMacro extends Macro {
  @Override
  public String getName() {
    return "suggestIndexName";
  }

  @Override
  public @NotNull String getDefaultValue() {
    return "a";
  }

  @Override
  public Result calculateResult(Expression @NotNull [] params, final ExpressionContext context) {
    if (params.length != 0) return null;

    final Project project = context.getProject();
    final int offset = context.getStartOffset();

    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(context.getEditor().getDocument());
    PsiElement place = file.findElementAt(offset);
    PsiVariable[] vars = MacroUtil.getVariablesVisibleAt(place, "");
  ChooseLetterLoop:
    for(char letter = 'i'; letter <= 'z'; letter++){
      for (PsiVariable var : vars) {
        PsiIdentifier identifier = var.getNameIdentifier();
        if (identifier == null || place.equals(identifier)) continue;
        if (var instanceof PsiLocalVariable) {
          PsiElement parent = var.getParent();
          if (parent instanceof PsiDeclarationStatement) {
            if (PsiTreeUtil.isAncestor(parent, place, false) &&
                var.getTextRange().getStartOffset() > place.getTextRange().getStartOffset()) {
              continue;
            }
          }
        }
        String name = identifier.getText();
        if (name.length() == 1 && name.charAt(0) == letter) {
          continue ChooseLetterLoop;
        }
      }
      return new TextResult(String.valueOf(letter));
    }

    return null;
  }

  @Override
  public boolean isAcceptableInContext(TemplateContextType context) {
    return context instanceof JavaCodeContextType;
  }

}