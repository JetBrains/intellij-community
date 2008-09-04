package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

public class SuggestIndexNameMacro implements Macro{
  public String getName() {
    return "suggestIndexName";
  }

  public String getDescription() {
    return CodeInsightBundle.message("macro.suggest.index.name");
  }

  public String getDefaultValue() {
    return "a";
  }

  public Result calculateResult(@NotNull Expression[] params, final ExpressionContext context) {
    if (params.length != 0) return null;

    final Project project = context.getProject();
    final int offset = context.getStartOffset();

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(context.getEditor().getDocument());
    PsiElement place = file.findElementAt(offset);
    PsiVariable[] vars = MacroUtil.getVariablesVisibleAt(place, "");
  ChooseLetterLoop:
    for(char letter = 'i'; letter <= 'z'; letter++){
      for (PsiVariable var : vars) {
        PsiIdentifier identifier = var.getNameIdentifier();
        if (place.equals(identifier)) continue;
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
      return new TextResult("" + letter);
    }

    return null;
  }

  public Result calculateQuickResult(@NotNull Expression[] params, ExpressionContext context) {
    return null;
  }

  public LookupElement[] calculateLookupItems(@NotNull Expression[] params, ExpressionContext context) {
    return null;
  }
}