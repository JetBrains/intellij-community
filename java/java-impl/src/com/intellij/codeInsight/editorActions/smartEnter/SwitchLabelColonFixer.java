/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiCaseLabelElementList;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSwitchBlock;
import com.intellij.psi.PsiSwitchLabelStatement;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.SwitchUtils;
import org.jetbrains.annotations.NotNull;

public class SwitchLabelColonFixer implements Fixer {
  @Override
  public void apply(Editor editor, AbstractBasicJavaSmartEnterProcessor processor, @NotNull ASTNode astNode) throws IncorrectOperationException {
    PsiElement psiElement = BasicJavaAstTreeUtil.toPsi(astNode);
    if (psiElement instanceof PsiSwitchLabelStatement statement) {
      PsiSwitchBlock block = statement.getEnclosingSwitchBlock();
      if (block == null) return;
      String token = SwitchUtils.isRuleFormatSwitch(block) ? "->" : ":";
      if (!psiElement.getText().endsWith(token)) {
        PsiCaseLabelElementList labelElementList = statement.getCaseLabelElementList();
        if ((labelElementList != null && labelElementList.getElementCount() != 0) || statement.isDefaultCase()) {
          editor.getDocument().insertString(psiElement.getTextRange().getEndOffset(), token);
        }
      }
    }
  }
}
