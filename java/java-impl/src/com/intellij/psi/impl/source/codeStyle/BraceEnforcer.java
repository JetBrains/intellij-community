/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.codeStyle;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.jsp.JavaJspRecursiveElementVisitor;
import com.intellij.psi.jsp.JspFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class BraceEnforcer extends JavaJspRecursiveElementVisitor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.codeStyle.BraceEnforcer");

  private final AbstractPostFormatProcessor myPostProcessor;

  public BraceEnforcer(CodeStyleSettings settings) {
    myPostProcessor = new AbstractPostFormatProcessor(settings);
  }

  @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
    visitElement(expression);
  }

  @Override public void visitIfStatement(PsiIfStatement statement) {
    if (checkElementContainsRange(statement)) {
      final SmartPsiElementPointer pointer = SmartPointerManager.getInstance(statement.getProject()).createSmartPsiElementPointer(statement);
      super.visitIfStatement(statement);
      statement = (PsiIfStatement)pointer.getElement();
      if (statement == null) {
        return;
      }
      processStatement(statement, statement.getThenBranch(), myPostProcessor.mySettings.IF_BRACE_FORCE);
      final PsiStatement elseBranch = statement.getElseBranch();
      if (!(elseBranch instanceof PsiIfStatement) || !myPostProcessor.mySettings.SPECIAL_ELSE_IF_TREATMENT) {
        processStatement(statement, elseBranch, myPostProcessor.mySettings.IF_BRACE_FORCE);
      }
    }
  }

  @Override public void visitForStatement(PsiForStatement statement) {
    if (checkElementContainsRange(statement)) {
      super.visitForStatement(statement);
      processStatement(statement, statement.getBody(), myPostProcessor.mySettings.FOR_BRACE_FORCE);
    }
  }

  @Override public void visitForeachStatement(PsiForeachStatement statement) {
    if (checkElementContainsRange(statement)) {
      super.visitForeachStatement(statement);
      processStatement(statement, statement.getBody(), myPostProcessor.mySettings.FOR_BRACE_FORCE);
    }
  }

  @Override public void visitWhileStatement(PsiWhileStatement statement) {
    if (checkElementContainsRange(statement)) {
      super.visitWhileStatement(statement);
      processStatement(statement, statement.getBody(), myPostProcessor.mySettings.WHILE_BRACE_FORCE);
    }
  }

  @Override public void visitDoWhileStatement(PsiDoWhileStatement statement) {
    if (checkElementContainsRange(statement)) {
      super.visitDoWhileStatement(statement);
      processStatement(statement, statement.getBody(), myPostProcessor.mySettings.DOWHILE_BRACE_FORCE);
    }
  }

  @Override public void visitJspFile(JspFile file) {
    final PsiClass javaRoot = file.getJavaClass();
    if (javaRoot != null) {
      javaRoot.accept(this);
    }
  }
  
  private void processStatement(PsiStatement statement, PsiStatement blockCandidate, int options) {
    if (blockCandidate instanceof PsiBlockStatement || blockCandidate == null) return;
    if (options == CodeStyleSettings.FORCE_BRACES_ALWAYS ||
        options == CodeStyleSettings.FORCE_BRACES_IF_MULTILINE && AbstractPostFormatProcessor.isMultiline(statement)) {
      replaceWithBlock(statement, blockCandidate);
    }
  }

  private void replaceWithBlock(@NotNull PsiStatement statement, PsiStatement blockCandidate) {
    if (!statement.isValid()) {
      LOG.assertTrue(false);
    }

    if (!checkRangeContainsElement(blockCandidate)) return;

    final PsiManager manager = statement.getManager();
    LOG.assertTrue(manager != null);
    final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    
    String oldText = blockCandidate.getText();
    StringBuilder buf = new StringBuilder(oldText.length() + 5);

    // There is a possible case that target block to wrap ends with single-line comment. Example:
    //     if (true) i = 1; // Cool assignement
    // We can't just surround target block of code with curly braces because the closing one will be treated as comment as well.
    // Hence, we perform a check if we have such situation at the moment and insert new line before the closing brace.
    int lastLineFeedIndex = oldText.lastIndexOf("\n");
    lastLineFeedIndex = Math.max(0, lastLineFeedIndex);
    int lastLineCommentIndex = oldText.indexOf("//", lastLineFeedIndex);
    buf.append("{ ").append(oldText);
    if (lastLineCommentIndex >= 0) {
      buf.append("\n");
    }
    buf.append(" }");

    final int oldTextLength = statement.getTextLength();
    try {
      CodeEditUtil.replaceChild(SourceTreeToPsiMap.psiElementToTree(statement),
                                SourceTreeToPsiMap.psiElementToTree(blockCandidate),
                                SourceTreeToPsiMap.psiElementToTree(factory.createCodeBlockFromText(buf.toString(), null)));
      CodeStyleManager.getInstance(statement.getProject()).reformat(statement, true);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    finally {
      updateResultRange(oldTextLength , statement.getTextLength());
    }
  }

  protected void updateResultRange(final int oldTextLength, final int newTextLength) {
    myPostProcessor.updateResultRange(oldTextLength, newTextLength);
  }

  protected boolean checkElementContainsRange(final PsiElement element) {
    return myPostProcessor.checkElementContainsRange(element);
  }

  protected boolean checkRangeContainsElement(final PsiElement element) {
    return myPostProcessor.checkRangeContainsElement(element);
  }

  public PsiElement process(PsiElement formatted) {
    LOG.assertTrue(formatted.isValid());
    formatted.accept(this);
    return formatted;

  }

  public TextRange processText(final PsiFile source, final TextRange rangeToReformat) {
    myPostProcessor.setResultTextRange(rangeToReformat);
    source.accept(this);
    return myPostProcessor.getResultTextRange();
  }
}
