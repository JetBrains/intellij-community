// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.impl.source.BasicJavaElementType.BASIC_ANNOTATION_PARAMETER_LIST;
import static com.intellij.psi.impl.source.BasicJavaElementType.BASIC_PARAMETER_LIST;

public class ParameterListFixer implements Fixer {

  @Override
  public void apply(Editor editor, AbstractBasicJavaSmartEnterProcessor processor, @NotNull ASTNode astNode) throws IncorrectOperationException {
    if (BasicJavaAstTreeUtil.is(astNode, BASIC_PARAMETER_LIST) ||
        BasicJavaAstTreeUtil.is(astNode, BASIC_ANNOTATION_PARAMETER_LIST)) {
      String text = astNode.getText();
      if (StringUtil.startsWithChar(text, '(') && !StringUtil.endsWithChar(text, ')')) {
        ASTNode[] params = BasicJavaAstTreeUtil.is(astNode, BASIC_PARAMETER_LIST) ?
                           BasicJavaAstTreeUtil.getParameterListParameters(astNode) :
                           BasicJavaAstTreeUtil.getAnnotationParameterListAttributes(astNode);
        int offset;
        if (params.length == 0) {
          offset = astNode.getTextRange().getStartOffset() + 1;
        }
        else {
          offset = params[params.length - 1].getTextRange().getEndOffset();
        }
        editor.getDocument().insertString(offset, ")");
      }
    }
  }
}
