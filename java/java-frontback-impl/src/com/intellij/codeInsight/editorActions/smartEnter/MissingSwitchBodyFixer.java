// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.impl.source.BasicJavaElementType.BASIC_SWITCH_EXPRESSION;
import static com.intellij.psi.impl.source.BasicJavaElementType.BASIC_SWITCH_STATEMENT;

public class MissingSwitchBodyFixer implements Fixer {
  @Override
  public void apply(Editor editor, AbstractBasicJavaSmartEnterProcessor processor, @NotNull ASTNode astNode) throws IncorrectOperationException {
    if (!(BasicJavaAstTreeUtil.is(astNode, BASIC_SWITCH_EXPRESSION, BASIC_SWITCH_STATEMENT))) return;

    final ASTNode body = BasicJavaAstTreeUtil.getCodeBlock(astNode);
    if (body != null) return;

    final ASTNode rParenth = BasicJavaAstTreeUtil.getRParenth(astNode);
    assert rParenth != null;

    int offset = rParenth.getTextRange().getEndOffset();
    processor.insertBracesWithNewLine(editor, offset);
    if (BasicJavaAstTreeUtil.is(astNode, BASIC_SWITCH_EXPRESSION)) {
      Project project = editor.getProject();
      PsiElement psiElement = BasicJavaAstTreeUtil.toPsi(astNode);
      if (project != null && psiElement != null) {
        SmartPsiElementPointer<PsiElement> pointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(psiElement);
        processor.commit(editor);
        PsiElement reparsedSwitch = pointer.getElement();
        if (reparsedSwitch != null) {
          editor.getCaretModel().moveToOffset(offset + 1);
          processor.reformat(editor, pointer.getElement());
          processor.setSkipEnter(true);
        }
      }
    }
  }
}