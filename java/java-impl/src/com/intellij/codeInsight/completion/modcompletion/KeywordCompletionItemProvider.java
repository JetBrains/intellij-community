// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.modcompletion;

import com.intellij.codeInsight.JavaTailTypes;
import com.intellij.codeInsight.ModNavigatorTailType;
import com.intellij.codeInsight.TailTypes;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcompletion.CompletionItem;
import com.intellij.modcompletion.CompletionItemProvider;
import com.intellij.psi.*;
import com.intellij.psi.filters.FilterPositionUtil;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

import static com.intellij.patterns.PsiJavaPatterns.psiAnnotation;
import static com.intellij.patterns.PsiJavaPatterns.psiElement;
import static com.intellij.patterns.StandardPatterns.string;

/**
 * A provider for Java keywords completion.
 */
@NotNullByDefault
final class KeywordCompletionItemProvider implements CompletionItemProvider {
  @Override
  public void provideItems(CompletionContext context, Consumer<CompletionItem> sink) {
    PsiElement element = context.element();
    if (!context.isSmart()) {
      if (canAddKeywords(element)) {
        if (isStatementPosition(element)) {
          sink.accept(new KeywordCompletionItem(JavaKeywords.SWITCH, JavaTailTypes.SWITCH_LPARENTH));
          sink.accept(new KeywordCompletionItem(JavaKeywords.WHILE, JavaTailTypes.WHILE_LPARENTH));
          //sink.accept(new KeywordCompletionItem(JavaKeywords.DO, JavaTailTypes.DO_LBRACE));
          sink.accept(new KeywordCompletionItem(JavaKeywords.FOR, JavaTailTypes.FOR_LPARENTH));
          sink.accept(new KeywordCompletionItem(JavaKeywords.IF, JavaTailTypes.IF_LPARENTH));
          //sink.accept(new KeywordCompletionItem(JavaKeywords.TRY, JavaTailTypes.TRY_LBRACE));
          sink.accept(new KeywordCompletionItem(JavaKeywords.SYNCHRONIZED, JavaTailTypes.SYNCHRONIZED_LPARENTH));
          sink.accept(new KeywordCompletionItem(JavaKeywords.THROW, (ModNavigatorTailType)TailTypes.insertSpaceType()));
          sink.accept(new KeywordCompletionItem(JavaKeywords.NEW, (ModNavigatorTailType)TailTypes.insertSpaceType()));
        }
      }
    }
  }

  private static boolean canAddKeywords(PsiElement position) {
    if (PsiTreeUtil.getNonStrictParentOfType(position, PsiLiteralExpression.class, PsiComment.class) != null) {
      return false;
    }

    if (psiElement().afterLeaf("::").accepts(position)) {
      return false;
    }
    return true;
  }

  private static boolean isStatementPosition(PsiElement position) {
    if (psiElement()
      .withSuperParent(2, PsiConditionalExpression.class)
      .andNot(psiElement().insideStarting(psiElement(PsiConditionalExpression.class)))
      .accepts(position)) {
      return false;
    }

    if (isEndOfBlock(position) &&
        PsiTreeUtil.getParentOfType(position, PsiCodeBlock.class, true, PsiMember.class) != null) {
      return !isForLoopMachinery(position);
    }

    if (psiElement().withParents(PsiReferenceExpression.class, PsiExpressionStatement.class, PsiIfStatement.class).andNot(
      psiElement().afterLeaf(".")).accepts(position)) {
      PsiElement stmt = position.getParent().getParent();
      PsiIfStatement ifStatement = (PsiIfStatement)stmt.getParent();
      return ifStatement.getElseBranch() == stmt || ifStatement.getThenBranch() == stmt;
    }

    return false;
  }

  static boolean isEndOfBlock(PsiElement element) {
    PsiElement prev = prevSignificantLeaf(element);
    if (prev == null) {
      PsiFile file = element.getContainingFile();
      return !(file instanceof PsiCodeFragment) || isStatementCodeFragment(file);
    }

    if (psiElement().inside(psiAnnotation()).accepts(prev)) return false;

    if (prev instanceof OuterLanguageElement) return true;
    if (psiElement().withText(string().oneOf("{", "}", ";", ":", "else")).accepts(prev)) return true;
    if (prev.textMatches(")")) {
      PsiElement parent = prev.getParent();
      if (parent instanceof PsiParameterList) {
        return PsiTreeUtil.getParentOfType(PsiTreeUtil.prevVisibleLeaf(element), PsiDocComment.class) != null;
      }

      return !(parent instanceof PsiExpressionList || parent instanceof PsiTypeCastExpression
               || parent instanceof PsiRecordHeader);
    }

    return false;
  }

  private static @Nullable PsiElement prevSignificantLeaf(PsiElement position) {
    return FilterPositionUtil.searchNonSpaceNonCommentBack(position);
  }

  private static boolean isStatementCodeFragment(PsiFile file) {
    return file instanceof JavaCodeFragment &&
           !(file instanceof PsiExpressionCodeFragment ||
             file instanceof PsiJavaCodeReferenceCodeFragment ||
             file instanceof PsiTypeCodeFragment);
  }

  private static boolean isForLoopMachinery(PsiElement position) {
    PsiStatement statement = PsiTreeUtil.getParentOfType(position, PsiStatement.class);
    if (statement == null) return false;

    return statement instanceof PsiForStatement ||
           statement.getParent() instanceof PsiForStatement && statement != ((PsiForStatement)statement.getParent()).getBody();
  }
}
