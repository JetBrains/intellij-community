// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiCaseLabelElementList;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSwitchBlock;
import com.intellij.psi.PsiSwitchLabelStatement;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class SwitchLabelColonFixer implements Fixer {
  @Override
  public void apply(Editor editor, AbstractBasicJavaSmartEnterProcessor processor, @NotNull ASTNode astNode) throws IncorrectOperationException {
    PsiElement psiElement = BasicJavaAstTreeUtil.toPsi(astNode);
    if (psiElement instanceof PsiSwitchLabelStatement statement) {
      PsiSwitchBlock block = statement.getEnclosingSwitchBlock();
      if (block == null) return;
      String token = PsiUtil.isRuleFormatSwitch(block) ? "->" : ":";
      if (!psiElement.getText().endsWith(token)) {
        PsiCaseLabelElementList labelElementList = statement.getCaseLabelElementList();
        if ((labelElementList != null && labelElementList.getElementCount() != 0) || statement.isDefaultCase()) {
          editor.getDocument().insertString(psiElement.getTextRange().getEndOffset(), token);
        }
      }
    }
  }
}
