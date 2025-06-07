// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.impl.source.BasicJavaElementType.BASIC_BLOCK_STATEMENT;
import static com.intellij.psi.impl.source.BasicJavaElementType.BASIC_IF_STATEMENT;

public class MissingIfBranchesFixer implements Fixer {
  @Override
  public void apply(Editor editor, AbstractBasicJavaSmartEnterProcessor processor, @NotNull ASTNode astNode) throws IncorrectOperationException {
    if (!(BasicJavaAstTreeUtil.is(astNode, BASIC_IF_STATEMENT))) return;

    final Document doc = editor.getDocument();
    final ASTNode elseElement = BasicJavaAstTreeUtil.getElseElement(astNode);
    if (elseElement != null) {
      handleBranch(doc, astNode, elseElement, BasicJavaAstTreeUtil.getElseBranch(astNode));
    }

    ASTNode rParenth = BasicJavaAstTreeUtil.getRParenth(astNode);
    assert rParenth != null;
    handleBranch(doc, astNode, rParenth, BasicJavaAstTreeUtil.getThenBranch(astNode));
  }

  private static void handleBranch(@NotNull Document doc,
                            @NotNull ASTNode ifStatement,
                            @NotNull ASTNode beforeBranch,
                            @Nullable ASTNode branch) {
    if (BasicJavaAstTreeUtil.is(branch, BASIC_BLOCK_STATEMENT) ||
        JavaKeywords.ELSE.equals(beforeBranch.getText()) && BasicJavaAstTreeUtil.is(branch, BASIC_IF_STATEMENT)) {
      return;
    }
    boolean transformingOneLiner = branch != null && (startLine(doc, beforeBranch) == startLine(doc, branch) ||
                                                      startCol(doc, ifStatement) < startCol(doc, branch));

    if (!transformingOneLiner) {
      doc.insertString(beforeBranch.getTextRange().getEndOffset(), "{}");
    }
    else {
      doc.insertString(beforeBranch.getTextRange().getEndOffset(), "{");
      doc.insertString(branch.getTextRange().getEndOffset() + 1, "}");
    }
  }

  private static int startLine(Document doc, @NotNull ASTNode astNode) {
    return doc.getLineNumber(astNode.getTextRange().getStartOffset());
  }

  private static int startCol(Document doc, @NotNull ASTNode astNode) {
    int offset = astNode.getTextRange().getStartOffset();
    return offset - doc.getLineStartOffset(doc.getLineNumber(offset));
  }
}
