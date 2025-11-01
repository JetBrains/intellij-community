// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.modcompletion;

import com.intellij.codeInsight.JavaTailTypes;
import com.intellij.codeInsight.ModNavigatorTailType;
import com.intellij.codeInsight.TailTypes;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcompletion.CompletionItem;
import com.intellij.modcompletion.CompletionItemProvider;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.filters.FilterPositionUtil;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

import static com.intellij.patterns.PsiJavaPatterns.psiAnnotation;
import static com.intellij.patterns.PsiJavaPatterns.psiElement;
import static com.intellij.patterns.StandardPatterns.or;
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
          addStatementKeywords(context, sink);
        }
      }
    }
  }

  private static void addStatementKeywords(CompletionContext context, Consumer<CompletionItem> sink) {
    PsiElement element = context.element();
    PsiElement prevLeaf = FilterPositionUtil.searchNonSpaceNonCommentBack(element);
    if (psiElement()
      .withText("}")
      .withParent(psiElement(PsiCodeBlock.class).withParent(or(psiElement(PsiTryStatement.class), psiElement(PsiCatchSection.class))))
      .accepts(prevLeaf)) {
      sink.accept(new KeywordCompletionItem(JavaKeywords.CATCH, JavaTailTypes.CATCH_LPARENTH));
      sink.accept(new KeywordCompletionItem(JavaKeywords.FINALLY, JavaTailTypes.FINALLY_LBRACE));
      if (prevLeaf != null && prevLeaf.getParent().getNextSibling() instanceof PsiErrorElement) {
        return;
      }
    }
    sink.accept(new KeywordCompletionItem(JavaKeywords.SWITCH, JavaTailTypes.SWITCH_LPARENTH));
    sink.accept(new KeywordCompletionItem(JavaKeywords.WHILE, JavaTailTypes.WHILE_LPARENTH));
    sink.accept(new KeywordCompletionItem(JavaKeywords.DO, JavaTailTypes.DO_LBRACE));
    sink.accept(new KeywordCompletionItem(JavaKeywords.FOR, JavaTailTypes.FOR_LPARENTH));
    sink.accept(new KeywordCompletionItem(JavaKeywords.IF, JavaTailTypes.IF_LPARENTH));
    sink.accept(new KeywordCompletionItem(JavaKeywords.TRY, JavaTailTypes.TRY_LBRACE));
    sink.accept(new KeywordCompletionItem(JavaKeywords.SYNCHRONIZED, JavaTailTypes.SYNCHRONIZED_LPARENTH));
    sink.accept(new KeywordCompletionItem(JavaKeywords.THROW, (ModNavigatorTailType)TailTypes.insertSpaceType()));
    sink.accept(new KeywordCompletionItem(JavaKeywords.NEW, (ModNavigatorTailType)TailTypes.insertSpaceType()));
    if (PsiUtil.isAvailable(JavaFeature.ASSERTIONS, element)) {
      sink.accept(new KeywordCompletionItem(JavaKeywords.ASSERT, (ModNavigatorTailType)TailTypes.insertSpaceType()));
    }
    if (!(PsiTreeUtil.getParentOfType(element, PsiSwitchExpression.class, PsiLambdaExpression.class) 
            instanceof PsiSwitchExpression)) {
      sink.accept(new KeywordCompletionItem(JavaKeywords.RETURN, getReturnTail(element)));
    }
    if (psiElement().withText(";").withSuperParent(2, PsiIfStatement.class).accepts(prevLeaf) ||
        psiElement().withText("}").withSuperParent(3, PsiIfStatement.class).accepts(prevLeaf)) {
      CompletionItem elseKeyword = new KeywordCompletionItem(JavaKeywords.ELSE, (ModNavigatorTailType)TailTypes.humbleSpaceBeforeWordType());
      CharSequence text = element.getContainingFile().getFileDocument().getCharsSequence();
      int offset = context.offset();
      while (text.length() > offset && Character.isWhitespace(text.charAt(offset))) {
        offset++;
      }
      if (text.length() > offset + JavaKeywords.ELSE.length() &&
          text.subSequence(offset, offset + JavaKeywords.ELSE.length()).toString().equals(JavaKeywords.ELSE) &&
          Character.isWhitespace(text.charAt(offset + JavaKeywords.ELSE.length()))) {
        //TODO: priority
        //elseKeyword = PrioritizedLookupElement.withPriority(elseKeyword, -1);
      }
      sink.accept(elseKeyword);
    }
  }

  private static ModNavigatorTailType getReturnTail(PsiElement position) {
    PsiElement scope = position;
    while (true) {
      if (scope instanceof PsiFile || scope instanceof PsiClassInitializer) {
        return (ModNavigatorTailType)TailTypes.noneType();
      }

      if (scope instanceof PsiMethod method) {
        if (method.isConstructor() || PsiTypes.voidType().equals(method.getReturnType())) {
          return (ModNavigatorTailType)TailTypes.semicolonType();
        }

        return (ModNavigatorTailType)TailTypes.humbleSpaceBeforeWordType();
      }
      if (scope instanceof PsiLambdaExpression lambda) {
        final PsiType returnType = LambdaUtil.getFunctionalInterfaceReturnType(lambda);
        if (PsiTypes.voidType().equals(returnType)) {
          return (ModNavigatorTailType)TailTypes.semicolonType();
        }
        return (ModNavigatorTailType)TailTypes.humbleSpaceBeforeWordType();
      }
      scope = scope.getParent();
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
